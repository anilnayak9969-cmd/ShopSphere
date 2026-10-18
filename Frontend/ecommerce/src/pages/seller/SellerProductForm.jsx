import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import sellerService from '../../services/sellerService'
import categoryService, { toCategoryTree } from '../../services/categoryService'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import { Field } from '../../components/FormControls'
import ImageUpload from '../../components/ImageUpload'
import { Alert, ErrorState, PageHeader, PageLoader, PriceTag, SafeImage, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { SKU_RE, IMAGE_URL_RE } from '../../utils/validators'
import SellerStatusBanner from './SellerStatusBanner'

const MAX_GALLERY = 5
const MAX_SPECS = 20

const EMPTY = {
  productName: '',
  description: '',
  sku: '',
  brand: '',
  price: '',
  discountPercent: '0',
  stockQuantity: '',
  imageUrl: '',
  categoryId: '',
  galleryImageUrls: [],
  specifications: [],
}

function validate(form) {
  const e = {}
  if (form.productName.trim().length < 2) e.productName = 'Product name must be 2-150 characters'
  if (form.description.trim().length < 10) e.description = 'Description must be at least 10 characters'
  if (!SKU_RE.test(form.sku.trim())) e.sku = 'SKU must be 3-60 letters, numbers, - or _'
  const price = Number(form.price)
  if (!form.price || Number.isNaN(price) || price < 1) e.price = 'Price must be at least ₹1'
  else if (!/^\d+(\.\d{1,2})?$/.test(form.price)) e.price = 'Use at most 2 decimal places'
  const discount = Number(form.discountPercent)
  if (!Number.isInteger(discount) || discount < 0 || discount > 90) e.discountPercent = 'Discount must be 0-90%'
  const stock = Number(form.stockQuantity)
  if (form.stockQuantity === '' || !Number.isInteger(stock) || stock < 0 || stock > 100000) {
    e.stockQuantity = 'Enter a whole number from 0 to 100000'
  }
  if (form.imageUrl.trim() && !IMAGE_URL_RE.test(form.imageUrl.trim())) e.imageUrl = 'Upload an image or enter a link starting with http(s)://'
  if (!form.categoryId) e.categoryId = 'Choose a category'
  const badGallery = form.galleryImageUrls.findIndex((u) => u.trim() && !IMAGE_URL_RE.test(u.trim()))
  if (badGallery >= 0) e.galleryImageUrls = `Photo ${badGallery + 1}: upload an image or enter a link starting with http(s)://`
  const badSpec = form.specifications.findIndex((s) => (s.name.trim() || s.value.trim()) && (!s.name.trim() || !s.value.trim()))
  if (badSpec >= 0) e.specifications = `Specification ${badSpec + 1}: fill in both the name and the value, or remove the row`
  const names = form.specifications.map((s) => s.name.trim().toLowerCase()).filter(Boolean)
  if (!e.specifications && new Set(names).size !== names.length) e.specifications = 'Each specification name can be used only once'
  return e
}

/** Turns "/api/images/..." or a link into a preview-safe value (or null while the user is still typing). */
const previewable = (url) => (IMAGE_URL_RE.test(url.trim()) ? url.trim() : null)

export default function SellerProductForm() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()

  const [form, setForm] = useState(EMPTY)
  const [original, setOriginal] = useState(null)
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    const requests = [categoryService.getCategories()]
    if (isEdit) requests.push(sellerService.getProduct(id))
    Promise.all(requests)
      .then(([cats, product]) => {
        setCategories(cats)
        if (product) {
          setOriginal(product)
          setForm({
            productName: product.productName,
            description: product.description,
            sku: product.sku,
            brand: product.brand || '',
            price: String(product.price),
            discountPercent: String(product.discountPercent),
            stockQuantity: String(product.stockQuantity),
            imageUrl: product.imageUrl || '',
            categoryId: String(product.categoryId),
            galleryImageUrls: product.galleryImages || [],
            specifications: (product.specifications || []).map((s) => ({ name: s.name, value: s.value })),
          })
        }
      })
      .catch((err) => setLoadError(getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [id, isEdit])

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const setGallery = (index, value) =>
    setForm((f) => ({ ...f, galleryImageUrls: f.galleryImageUrls.map((u, i) => (i === index ? value : u)) }))
  const setSpec = (index, key, value) =>
    setForm((f) => ({ ...f, specifications: f.specifications.map((s, i) => (i === index ? { ...s, [key]: value } : s)) }))

  const preview = useMemo(() => {
    const price = Number(form.price) || 0
    const discount = Math.min(Math.max(Number(form.discountPercent) || 0, 0), 90)
    return { price, discount, selling: Math.round(price * (100 - discount)) / 100 }
  }, [form.price, form.discountPercent])

  if (loading) return <PageLoader />
  if (loadError) return <ErrorState message={loadError} onRetry={() => window.location.reload()} />

  const approved = user.sellerStatus === 'APPROVED'
  const tree = toCategoryTree(categories)
  const currentCategoryMissing = original && !categories.some((c) => c.categoryId === original.categoryId)

  const submit = async (event) => {
    event.preventDefault()
    const found = validate(form)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return

    const payload = {
      productName: form.productName.trim(),
      description: form.description.trim(),
      sku: form.sku.trim(),
      brand: form.brand.trim() || null,
      price: Number(form.price),
      discountPercent: Number(form.discountPercent),
      stockQuantity: Number(form.stockQuantity),
      imageUrl: form.imageUrl.trim() || null,
      categoryId: Number(form.categoryId),
      galleryImageUrls: form.galleryImageUrls.map((u) => u.trim()).filter(Boolean),
      specifications: form.specifications
        .map((s) => ({ name: s.name.trim(), value: s.value.trim() }))
        .filter((s) => s.name && s.value),
    }
    setSaving(true)
    try {
      const saved = isEdit ? await sellerService.updateProduct(id, payload) : await sellerService.createProduct(payload)
      if (saved.approvalStatus === 'PENDING') {
        toast.success(`${saved.productName} was sent for approval. It will appear in the store once approved.`)
      } else {
        toast.success('Product updated.')
      }
      navigate('/seller/products')
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader
        eyebrow={isEdit ? 'Edit listing' : 'New listing'}
        title={isEdit ? form.productName || 'Edit product' : 'Add a product'}
        subtitle="Prices are in rupees. Customers see the price after discount."
        actions={original && <StatusBadge status={original.approvalStatus} label={original.approvalStatus === 'PENDING' ? 'Awaiting approval' : undefined} />}
      />
      <SellerStatusBanner status={user.sellerStatus} />
      {original?.approvalStatus === 'REJECTED' && (
        <Alert variant="danger" className="mb-3">
          <strong>Not approved:</strong> {original.rejectionReason || 'No reason given.'} Fix the listing and save to send it for review again.
        </Alert>
      )}
      <Alert variant="info" className="mb-4">
        {isEdit
          ? 'Changing the name, description, brand, category, images or specifications sends the product for review again. Price, discount and stock changes go live immediately.'
          : 'New products are reviewed by the ShopSphere team before customers can see them. You will get a notification once it is approved.'}
      </Alert>
      {formError && (
        <Alert variant="danger" className="mb-4">
          {formError}
        </Alert>
      )}

      <form onSubmit={submit} noValidate className="row g-4">
        <div className="col-xl-8">
          <fieldset disabled={!approved || saving} className="ss-card ss-card-pad mb-4">
            <legend className="h5 mb-3">Basic details</legend>
            <div className="row g-3">
              <div className="col-12">
                <Field id="productName" label="Product name" value={form.productName} onChange={set('productName')} error={errors.productName} maxLength={150} />
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
                    <option value={String(original.categoryId)} disabled>
                      {original.categoryName} (inactive)
                    </option>
                  )}
                </Field>
              </div>
              <div className="col-md-6">
                <Field id="brand" label="Brand (optional)" value={form.brand} onChange={set('brand')} error={errors.brand} maxLength={80} />
              </div>
              <div className="col-md-6">
                <Field id="sku" label="SKU" value={form.sku} onChange={set('sku')} error={errors.sku} hint="Your unique stock code, e.g. WATCH-001" maxLength={60} />
              </div>
              <div className="col-md-6">
                <Field id="stockQuantity" type="number" min="0" max="100000" label="Stock quantity" value={form.stockQuantity} onChange={set('stockQuantity')} error={errors.stockQuantity} />
              </div>
              <div className="col-md-6">
                <Field id="price" type="number" min="1" step="0.01" label="Price / MRP (₹)" value={form.price} onChange={set('price')} error={errors.price} />
              </div>
              <div className="col-md-6">
                <Field id="discountPercent" type="number" min="0" max="90" label="Discount (%)" value={form.discountPercent} onChange={set('discountPercent')} error={errors.discountPercent} />
              </div>
            </div>
          </fieldset>

          <fieldset disabled={!approved || saving} className="ss-card ss-card-pad mb-4">
            <legend className="h5 mb-3">Photos</legend>
            <ImageUpload
              id="imageUrl"
              label="Main photo"
              value={form.imageUrl}
              onChange={(value) => setForm((f) => ({ ...f, imageUrl: value }))}
              error={errors.imageUrl}
              hint="Square JPG, PNG or WEBP up to 5 MB works best."
            />
            <div className="mt-4">
              <span className="form-label d-block">Gallery photos (optional, up to {MAX_GALLERY})</span>
              <div className="d-grid gap-3">
                {form.galleryImageUrls.map((url, index) => (
                  <div key={index} className="d-flex gap-2 align-items-start">
                    <div className="flex-grow-1 min-w-0">
                      <ImageUpload id={`gallery-${index}`} value={url} onChange={(value) => setGallery(index, value)} />
                    </div>
                    <button
                      type="button"
                      className="btn btn-ghost"
                      aria-label={`Remove gallery photo ${index + 1}`}
                      onClick={() => setForm((f) => ({ ...f, galleryImageUrls: f.galleryImageUrls.filter((_, i) => i !== index) }))}
                    >
                      <i className="bi bi-trash" />
                    </button>
                  </div>
                ))}
              </div>
              {errors.galleryImageUrls && <div className="invalid-feedback d-block">{errors.galleryImageUrls}</div>}
              {form.galleryImageUrls.length < MAX_GALLERY && (
                <button
                  type="button"
                  className="btn btn-ghost btn-sm mt-2"
                  onClick={() => setForm((f) => ({ ...f, galleryImageUrls: [...f.galleryImageUrls, ''] }))}
                >
                  <i className="bi bi-plus-lg me-1" /> Add gallery photo
                </button>
              )}
            </div>
          </fieldset>

          <fieldset disabled={!approved || saving} className="ss-card ss-card-pad">
            <legend className="h5 mb-1">Specifications</legend>
            <p className="small text-muted">Shown as a table on the product page, e.g. “Battery life — 30 hours”.</p>
            <div className="d-grid gap-2">
              {form.specifications.map((spec, index) => (
                <div key={index} className="row g-2 align-items-center">
                  <div className="col-5">
                    <input
                      className="form-control"
                      placeholder="Name (e.g. Material)"
                      aria-label={`Specification ${index + 1} name`}
                      maxLength={60}
                      value={spec.name}
                      onChange={(e) => setSpec(index, 'name', e.target.value)}
                    />
                  </div>
                  <div className="col">
                    <input
                      className="form-control"
                      placeholder="Value (e.g. Stainless steel)"
                      aria-label={`Specification ${index + 1} value`}
                      maxLength={255}
                      value={spec.value}
                      onChange={(e) => setSpec(index, 'value', e.target.value)}
                    />
                  </div>
                  <div className="col-auto">
                    <button
                      type="button"
                      className="btn btn-ghost"
                      aria-label={`Remove specification ${index + 1}`}
                      onClick={() => setForm((f) => ({ ...f, specifications: f.specifications.filter((_, i) => i !== index) }))}
                    >
                      <i className="bi bi-trash" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
            {errors.specifications && <div className="invalid-feedback d-block">{errors.specifications}</div>}
            {form.specifications.length < MAX_SPECS && (
              <button
                type="button"
                className="btn btn-ghost btn-sm mt-2"
                onClick={() => setForm((f) => ({ ...f, specifications: [...f.specifications, { name: '', value: '' }] }))}
              >
                <i className="bi bi-plus-lg me-1" /> Add specification
              </button>
            )}
          </fieldset>

          <div className="d-flex gap-2 mt-4">
            <button type="submit" className="btn btn-ink btn-lg" disabled={!approved || saving}>
              {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              {isEdit ? 'Save changes' : 'Submit for approval'}
            </button>
            <Link to="/seller/products" className="btn btn-ghost btn-lg">
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
