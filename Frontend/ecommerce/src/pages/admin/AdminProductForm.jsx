import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import adminService from '../../services/adminService'
import categoryService, { toCategoryTree } from '../../services/categoryService'
import { useToast } from '../../context/ToastContext'
import { Field } from '../../components/FormControls'
import ImageUpload from '../../components/ImageUpload'
import { ActiveBadge, Alert, ErrorState, PageHeader, PageLoader, PriceTag, SafeImage, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDateTime } from '../../utils/format'
import { IMAGE_URL_RE, SKU_RE } from '../../utils/validators'

/**
 * Product Management — "Add a new product" and "Update product details" for admins.
 * Fields from the assignment: product name, description, price, category, SKU and inventory count.
 */
const EMPTY = {
  sellerId: '',
  productName: '',
  description: '',
  sku: '',
  brand: '',
  price: '',
  discountPercent: '0',
  stockQuantity: '',
  imageUrl: '',
  categoryId: '',
  weightKg: '0.5',
}

function validate(form, isEdit) {
  const e = {}
  if (!isEdit && !form.sellerId) e.sellerId = 'Choose the store that sells this product'
  const name = form.productName.trim()
  if (name.length < 2 || name.length > 150) e.productName = 'Product name must be 2-150 characters'
  const description = form.description.trim()
  if (description.length < 10 || description.length > 2000) e.description = 'Description must be 10-2000 characters'
  if (!SKU_RE.test(form.sku.trim())) e.sku = 'SKU must be 3-60 letters, numbers, - or _'
  const price = Number(form.price)
  if (!form.price || Number.isNaN(price) || price < 1) e.price = 'Price must be at least ₹1'
  else if (!/^\d+(\.\d{1,2})?$/.test(String(form.price))) e.price = 'Use at most 2 decimal places'
  const discount = Number(form.discountPercent)
  if (!Number.isInteger(discount) || discount < 0 || discount > 90) e.discountPercent = 'Discount must be 0-90%'
  const stock = Number(form.stockQuantity)
  if (form.stockQuantity === '' || !Number.isInteger(stock) || stock < 0 || stock > 100000) {
    e.stockQuantity = 'Enter a whole number from 0 to 100000'
  }
  if (!form.categoryId) e.categoryId = 'Choose a category'
  const weight = Number(form.weightKg)
  if (form.weightKg === '' || Number.isNaN(weight) || weight < 0.05 || weight > 50) e.weightKg = 'Weight must be 0.05-50 kg'
  if (form.imageUrl.trim() && !IMAGE_URL_RE.test(form.imageUrl.trim())) e.imageUrl = 'Upload an image or enter a link starting with http(s)://'
  return e
}

const previewable = (url) => (IMAGE_URL_RE.test(url.trim()) ? url.trim() : null)

export default function AdminProductForm() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const toast = useToast()
  const navigate = useNavigate()

  const [form, setForm] = useState(EMPTY)
  const [original, setOriginal] = useState(null)
  const [categories, setCategories] = useState([])
  const [sellers, setSellers] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    const requests = [
      categoryService.getCategories(),
      isEdit
        ? adminService.getProduct(id)
        : adminService.getUsers({ role: 'SELLER', sellerStatus: 'APPROVED', size: 50 }),
    ]
    Promise.all(requests)
      .then(([cats, second]) => {
        setCategories(cats)
        if (isEdit) {
          setOriginal(second)
          setForm({
            sellerId: String(second.sellerId),
            productName: second.productName,
            description: second.description || '',
            sku: second.sku,
            brand: second.brand || '',
            price: String(second.price),
            discountPercent: String(second.discountPercent),
            stockQuantity: String(second.stockQuantity),
            imageUrl: second.imageUrl || '',
            categoryId: String(second.categoryId),
            weightKg: String(second.weightKg ?? '0.5'),
          })
        } else {
          setSellers((second.content || []).filter((s) => s.active))
        }
      })
      .catch((err) => setLoadError(getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [id, isEdit])

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const preview = useMemo(() => {
    const price = Number(form.price) || 0
    const discount = Math.min(Math.max(Number(form.discountPercent) || 0, 0), 90)
    return { price, discount, selling: Math.round(price * (100 - discount)) / 100 }
  }, [form.price, form.discountPercent])

  if (loading) return <PageLoader />
  if (loadError) return <ErrorState message={loadError} onRetry={() => window.location.reload()} />

  const tree = toCategoryTree(categories)
  const currentCategoryMissing = original && !categories.some((c) => c.categoryId === original.categoryId)

  const submit = async (event) => {
    event.preventDefault()
    const found = validate(form, isEdit)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return

    const product = {
      productName: form.productName.trim(),
      description: form.description.trim(),
      sku: form.sku.trim(),
      brand: form.brand.trim() || null,
      price: Number(form.price),
      discountPercent: Number(form.discountPercent),
      stockQuantity: Number(form.stockQuantity),
      imageUrl: form.imageUrl.trim() || null,
      categoryId: Number(form.categoryId),
      weightKg: Number(form.weightKg),
      // The admin form keeps the seller's gallery photos and specifications as they are.
      galleryImageUrls: original?.galleryImages || [],
      specifications: original?.specifications || [],
    }
    setSaving(true)
    try {
      const saved = isEdit
        ? await adminService.updateProduct(id, product)
        : await adminService.createProduct({ sellerId: Number(form.sellerId), product })
      toast.success(isEdit ? `Product "${saved.productName}" updated.` : `Product "${saved.productName}" added and live in the store.`)
      navigate('/admin/products')
    } catch (err) {
      const fieldErrors = getFieldErrors(err)
      // Nested create errors come back as "product.sku"; show them on the matching field.
      setErrors(Object.fromEntries(Object.entries(fieldErrors).map(([k, v]) => [k.replace(/^product\./, ''), v])))
      setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Product management"
        title={isEdit ? `Edit product` : 'Add a new product'}
        subtitle={
          isEdit
            ? `${original.productName} · ${original.storeName} · last updated ${formatDateTime(original.updatedAt)}`
            : 'Products added by an admin are approved and visible to customers straight away.'
        }
        actions={
          original && (
            <div className="d-flex gap-2 align-items-center">
              <StatusBadge status={original.approvalStatus} />
              <ActiveBadge active={original.active} />
            </div>
          )
        }
      />
      {formError && (
        <Alert variant="danger" className="mb-4">
          {formError}
        </Alert>
      )}
      {original && !original.active && (
        <Alert variant="warning" className="mb-4">
          This product is <strong>inactive</strong> (soft deleted), so customers cannot see it. Activate it from the product list when it should be sold again.
        </Alert>
      )}

      <form onSubmit={submit} noValidate className="row g-4">
        <div className="col-xl-8">
          <fieldset disabled={saving} className="ss-card ss-card-pad mb-4">
            <legend className="h5 mb-3">Product details</legend>
            <div className="row g-3">
              <div className="col-12">
                {isEdit ? (
                  <Field id="store" label="Sold by" value={original.storeName || '—'} disabled hint="The store that ships this product." onChange={() => {}} />
                ) : (
                  <Field id="sellerId" as="select" label="Sold by (store)" value={form.sellerId} onChange={set('sellerId')} error={errors.sellerId} hint="Only approved stores are listed. The store packs and ships the orders.">
                    <option value="">Choose a store</option>
                    {sellers.map((s) => (
                      <option key={s.id} value={String(s.id)}>
                        {s.storeName || s.fullName} — {s.email}
                      </option>
                    ))}
                  </Field>
                )}
              </div>
              <div className="col-12">
                <Field id="productName" label="Product name" value={form.productName} onChange={set('productName')} error={errors.productName} maxLength={150} hint={`${form.productName.length}/150 characters`} />
              </div>
              <div className="col-12">
                <Field
                  id="description"
                  as="textarea"
                  rows={5}
                  label="Description"
                  value={form.description}
                  onChange={set('description')}
                  error={errors.description}
                  maxLength={2000}
                  hint={`${form.description.length}/2000 characters`}
                />
              </div>
              <div className="col-md-6">
                <Field id="categoryId" as="select" label="Category" value={form.categoryId} onChange={set('categoryId')} error={errors.categoryId}>
                  <option value="">Select a category</option>
                  {tree.map((root) =>
                    root.children.length ? (
                      <optgroup key={root.categoryId} label={root.categoryName}>
                        <option value={String(root.categoryId)}>{root.categoryName} (general)</option>
                        {root.children.map((c) => (
                          <option key={c.categoryId} value={String(c.categoryId)}>
                            {c.categoryName}
                          </option>
                        ))}
                      </optgroup>
                    ) : (
                      <option key={root.categoryId} value={String(root.categoryId)}>
                        {root.categoryName}
                      </option>
                    ),
                  )}
                  {currentCategoryMissing && (
                    <option value={String(original.categoryId)}>{original.categoryName} (inactive)</option>
                  )}
                </Field>
              </div>
              <div className="col-md-6">
                <Field id="sku" label="SKU (Stock Keeping Unit)" value={form.sku} onChange={set('sku')} error={errors.sku} hint="Unique stock code, e.g. LAMP-BRASS-01" maxLength={60} />
              </div>
              <div className="col-md-4">
                <Field id="price" type="number" min="1" step="0.01" label="Price / MRP (₹)" value={form.price} onChange={set('price')} error={errors.price} />
              </div>
              <div className="col-md-4">
                <Field id="discountPercent" type="number" min="0" max="90" label="Discount (%)" value={form.discountPercent} onChange={set('discountPercent')} error={errors.discountPercent} />
              </div>
              <div className="col-md-4">
                <Field id="stockQuantity" type="number" min="0" max="100000" label="Inventory count" value={form.stockQuantity} onChange={set('stockQuantity')} error={errors.stockQuantity} hint="Units available in stock" />
              </div>
              <div className="col-md-6">
                <Field id="brand" label="Brand (optional)" value={form.brand} onChange={set('brand')} error={errors.brand} maxLength={80} />
              </div>
              <div className="col-md-6">
                <Field id="weightKg" type="number" min="0.05" max="50" step="0.05" label="Packed weight (kg)" value={form.weightKg} onChange={set('weightKg')} error={errors.weightKg} hint="Used to work out the shipping cost" />
              </div>
            </div>
          </fieldset>

          <fieldset disabled={saving} className="ss-card ss-card-pad">
            <legend className="h5 mb-3">Photo</legend>
            <ImageUpload
              id="imageUrl"
              label="Main photo (optional)"
              value={form.imageUrl}
              onChange={(value) => setForm((f) => ({ ...f, imageUrl: value }))}
              error={errors.imageUrl}
              hint="Square JPG, PNG or WEBP up to 5 MB works best."
            />
          </fieldset>

          <div className="d-flex gap-2 mt-4">
            <button type="submit" className="btn btn-ink btn-lg" disabled={saving}>
              {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              {isEdit ? 'Save changes' : 'Add product'}
            </button>
            <Link to="/admin/products" className="btn btn-ghost btn-lg">
              Cancel
            </Link>
          </div>
        </div>

        <div className="col-xl-4">
          <div className="ss-preview-card" style={{ position: 'sticky', top: 90 }}>
            <span className="ss-eyebrow">Customer preview</span>
            <div className="ss-product-card mt-3">
              <div className="ss-product-media">
                <SafeImage key={form.imageUrl} src={previewable(form.imageUrl)} alt="Preview" />
                {preview.discount > 0 && <span className="ss-product-tag">{preview.discount}% OFF</span>}
              </div>
              <div className="ss-product-body">
                <span className="ss-product-brand">{form.brand || categories.find((c) => String(c.categoryId) === form.categoryId)?.categoryName || 'Brand'}</span>
                <span className="ss-product-name">{form.productName || 'Product name'}</span>
                <PriceTag price={preview.price} sellingPrice={preview.selling} discountPercent={preview.discount} />
              </div>
            </div>
          </div>
        </div>
      </form>
    </>
  )
}
