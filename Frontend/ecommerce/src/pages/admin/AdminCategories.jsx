import { useCallback, useEffect, useState } from 'react'
import adminService from '../../services/adminService'
import categoryService from '../../services/categoryService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import Modal from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field } from '../../components/FormControls'
import ImageUpload from '../../components/ImageUpload'
import { ActiveBadge, Alert, EmptyState, ErrorState, PageHeader, PageLoader, SafeImage, StatCard } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDate } from '../../utils/format'
import { IMAGE_URL_RE } from '../../utils/validators'

const STATUS_FILTERS = [
  { label: 'All', value: '' },
  { label: 'Active', value: 'true' },
  { label: 'Inactive', value: 'false' },
]

const EMPTY_FORM = { categoryName: '', description: '', imageUrl: '', parentCategoryId: '' }

function validate(form) {
  const errors = {}
  const name = form.categoryName.trim()
  if (name.length < 2 || name.length > 100) errors.categoryName = 'Category name must be 2-100 characters'
  else if (!/^[\p{L}0-9][\p{L}0-9 &,'()./-]*$/u.test(name)) errors.categoryName = "Use letters, numbers, spaces and & , ' ( ) . / - only"
  const description = form.description.trim()
  if (description.length < 5 || description.length > 300) errors.description = 'Description must be 5-300 characters'
  if (form.imageUrl.trim() && !IMAGE_URL_RE.test(form.imageUrl.trim())) errors.imageUrl = 'Upload an image or enter a link starting with http(s)://'
  return errors
}

/**
 * Category Management module:
 * create, list (search + status filter + pagination), product counts, edit,
 * activate / deactivate (soft delete), and reassigning products before deactivation.
 */
export default function AdminCategories() {
  const toast = useToast()
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [counts, setCounts] = useState(null)

  // create / edit modal
  const [editor, setEditor] = useState(null) // null | { mode: 'create' } | { mode: 'edit', category }
  const [form, setForm] = useState(EMPTY_FORM)
  const [formErrors, setFormErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [parentOptions, setParentOptions] = useState([])

  // deactivate modal
  const [deactivating, setDeactivating] = useState(null)
  const [targetCategoryId, setTargetCategoryId] = useState('')
  const [activeCategories, setActiveCategories] = useState([])
  const [working, setWorking] = useState(false)

  const load = useCallback(() => {
    setError(null)
    adminService
      .getCategories({ search: debouncedSearch, active: status, page, size: 8 })
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
  }, [debouncedSearch, status, page])

  const loadCounts = useCallback(() => {
    Promise.all([
      adminService.getCategories({ size: 1 }),
      adminService.getCategories({ active: true, size: 1 }),
      adminService.getCategories({ active: false, size: 1 }),
    ])
      .then(([all, active, inactive]) =>
        setCounts({ all: all.totalElements, active: active.totalElements, inactive: inactive.totalElements }),
      )
      .catch(() => setCounts(null))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    loadCounts()
  }, [loadCounts])

  const refreshAll = () => {
    load()
    loadCounts()
  }

  // ------------------------------------------------------------ create / edit

  /** Active top-level categories can be parents. A category cannot be its own parent. */
  const loadParentOptions = (excludeId) =>
    categoryService
      .getCategories()
      .then((all) => setParentOptions(all.filter((c) => c.parentCategoryId == null && c.categoryId !== excludeId)))
      .catch(() => setParentOptions([]))

  const openCreate = () => {
    loadParentOptions(null)
    setEditor({ mode: 'create' })
    setForm(EMPTY_FORM)
    setFormErrors({})
    setFormError(null)
  }

  const openEdit = (category) => {
    loadParentOptions(category.categoryId)
    setEditor({ mode: 'edit', category })
    setForm({
      categoryName: category.categoryName,
      description: category.description || '',
      imageUrl: category.imageUrl || '',
      parentCategoryId: category.parentCategoryId ? String(category.parentCategoryId) : '',
    })
    setFormErrors({})
    setFormError(null)
  }

  const save = async (event) => {
    event.preventDefault()
    const found = validate(form)
    setFormErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return

    const payload = {
      categoryName: form.categoryName.trim(),
      description: form.description.trim(),
      imageUrl: form.imageUrl.trim(),
      parentCategoryId: form.parentCategoryId ? Number(form.parentCategoryId) : null,
    }
    setSaving(true)
    try {
      if (editor.mode === 'create') {
        const created = await adminService.createCategory(payload)
        toast.success(`Category “${created.categoryName}” created.`)
      } else {
        const updated = await adminService.updateCategory(editor.category.categoryId, payload)
        toast.success(`Category “${updated.categoryName}” updated.`)
      }
      setEditor(null)
      refreshAll()
    } catch (err) {
      const fieldErrors = getFieldErrors(err)
      // A duplicate name comes back as a 409 without field errors — show it on the name field.
      if (err.response?.data?.code === 'DUPLICATE_CATEGORY') fieldErrors.categoryName = getErrorMessage(err)
      setFormErrors(fieldErrors)
      if (!Object.keys(fieldErrors).length) setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  // ------------------------------------------------- activate / deactivate

  const openDeactivate = async (category) => {
    setDeactivating(category)
    setTargetCategoryId('')
    if (category.activeProductCount > 0) {
      try {
        const page = await adminService.getCategories({ active: true, size: 50 })
        setActiveCategories(page.content.filter((c) => c.categoryId !== category.categoryId))
      } catch {
        setActiveCategories([])
      }
    }
  }

  const reassign = async () => {
    setWorking(true)
    try {
      const response = await adminService.reassignProducts(deactivating.categoryId, Number(targetCategoryId))
      toast.success(response.message)
      const fresh = await adminService.getCategory(deactivating.categoryId)
      setDeactivating(fresh)
      load()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setWorking(false)
    }
  }

  const deactivate = async () => {
    setWorking(true)
    try {
      await adminService.deleteCategory(deactivating.categoryId)
      toast.success(`“${deactivating.categoryName}” has been deactivated.`)
      setDeactivating(null)
      refreshAll()
    } catch (err) {
      toast.error(getErrorMessage(err))
      // The backend refuses if products were added meanwhile — reload the real counts.
      adminService.getCategory(deactivating.categoryId).then(setDeactivating).catch(() => {})
    } finally {
      setWorking(false)
    }
  }

  const activate = async (category) => {
    try {
      await adminService.setCategoryActive(category.categoryId, true)
      toast.success(`“${category.categoryName}” is active again.`)
      refreshAll()
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const hasProducts = deactivating?.activeProductCount > 0

  return (
    <>
      <PageHeader
        eyebrow="Catalog"
        title="Category management"
        subtitle="Organise products into categories so customers can find them quickly."
        actions={
          <button type="button" className="btn btn-ink" onClick={openCreate}>
            <i className="bi bi-plus-lg me-2" />
            New category
          </button>
        }
      />

      <div className="row g-3 mb-4">
        <div className="col-sm-4">
          <StatCard label="Total categories" value={counts?.all ?? '—'} icon="bi-tags" />
        </div>
        <div className="col-sm-4">
          <StatCard label="Active" value={counts?.active ?? '—'} icon="bi-check2-circle" foot="Visible in the store" />
        </div>
        <div className="col-sm-4">
          <StatCard label="Inactive" value={counts?.inactive ?? '—'} icon="bi-archive" foot="Soft deleted, kept for history" />
        </div>
      </div>

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Search categories"
              aria-label="Search categories"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
            />
          </div>
          <div className="ss-segment ms-auto" role="group" aria-label="Filter by status">
            {STATUS_FILTERS.map((f) => (
              <button
                type="button"
                key={f.label}
                className={status === f.value ? 'active' : ''}
                aria-pressed={status === f.value}
                onClick={() => {
                  setStatus(f.value)
                  setPage(0)
                }}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {error ? (
          <ErrorState message={error} onRetry={load} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState
            icon="bi-tags"
            title={debouncedSearch || status ? 'No categories match' : 'No categories yet'}
            action={
              !debouncedSearch &&
              !status && (
                <button type="button" className="btn btn-ink" onClick={openCreate}>
                  Create the first category
                </button>
              )
            }
          >
            {debouncedSearch || status ? 'Try another search or filter.' : 'Categories group products in the storefront.'}
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Category</th>
                  <th>Products</th>
                  <th>Status</th>
                  <th>Last updated</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((c) => (
                  <tr key={c.categoryId}>
                    <td>
                      <div className="d-flex align-items-center gap-3">
                        <div className="ss-thumb">
                          <SafeImage src={c.imageUrl} alt="" />
                        </div>
                        <div className="min-w-0">
                          <div className="ss-cell-title">{c.categoryName}</div>
                          {c.parentCategoryName && (
                            <div className="ss-cell-sub fw-semibold">
                              <i className="bi bi-diagram-2 me-1" aria-hidden="true" />
                              Subcategory of {c.parentCategoryName}
                            </div>
                          )}
                          <div className="ss-cell-sub" title={c.description}>
                            {c.description}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="fw-bold">{c.productCount}</div>
                      <div className="ss-cell-sub">{c.activeProductCount} active</div>
                    </td>
                    <td>
                      <ActiveBadge active={c.active} />
                    </td>
                    <td className="text-muted small">{formatDate(c.updatedAt)}</td>
                    <td className="text-end text-nowrap">
                      <button type="button" className="btn btn-ghost btn-sm me-2" onClick={() => openEdit(c)}>
                        <i className="bi bi-pencil me-1" aria-hidden="true" />
                        Edit
                      </button>
                      {c.active ? (
                        <button type="button" className="btn btn-danger-soft btn-sm" onClick={() => openDeactivate(c)}>
                          Deactivate
                        </button>
                      ) : (
                        <button type="button" className="btn btn-outline-ink btn-sm" onClick={() => activate(c)}>
                          Activate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}

      {/* Create / edit */}
      <Modal
        open={Boolean(editor)}
        title={editor?.mode === 'edit' ? 'Edit category' : 'New category'}
        subtitle={editor?.mode === 'edit' ? `Created ${formatDate(editor.category.createdAt)}` : 'Give it a clear name and a short description.'}
        onClose={() => !saving && setEditor(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setEditor(null)} disabled={saving}>
              Cancel
            </button>
            <button type="submit" form="category-form" className="btn btn-ink" disabled={saving}>
              {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              {editor?.mode === 'edit' ? 'Save changes' : 'Create category'}
            </button>
          </>
        }
      >
        {formError && (
          <Alert variant="danger" className="mb-3">
            {formError}
          </Alert>
        )}
        <form id="category-form" onSubmit={save} noValidate className="d-grid gap-3">
          <Field id="categoryName" label="Category name" value={form.categoryName} onChange={set('categoryName')} error={formErrors.categoryName} maxLength={100} />
          <Field
            id="description"
            as="textarea"
            rows={3}
            label="Description"
            value={form.description}
            onChange={set('description')}
            error={formErrors.description}
            maxLength={300}
            hint={`${form.description.length}/300 characters`}
          />
          <Field
            id="parentCategoryId"
            as="select"
            label="Parent category"
            value={form.parentCategoryId}
            onChange={set('parentCategoryId')}
            error={formErrors.parentCategoryId}
            hint="Leave as “None” for a main department, or pick one to make this a subcategory."
          >
            <option value="">None (main category)</option>
            {parentOptions.map((c) => (
              <option key={c.categoryId} value={String(c.categoryId)}>
                {c.categoryName}
              </option>
            ))}
            {editor?.category?.parentCategoryId && !parentOptions.some((c) => c.categoryId === editor.category.parentCategoryId) && (
              <option value={String(editor.category.parentCategoryId)}>{editor.category.parentCategoryName}</option>
            )}
          </Field>
          <ImageUpload
            id="imageUrl"
            label="Category image (optional)"
            value={form.imageUrl}
            onChange={(value) => setForm((f) => ({ ...f, imageUrl: value }))}
            error={formErrors.imageUrl}
            hint="Shown on the category tile on the home page."
          />
        </form>
      </Modal>

      {/* Deactivate (soft delete) with the "reassign products first" warning */}
      <Modal
        open={Boolean(deactivating)}
        title={`Deactivate “${deactivating?.categoryName}”?`}
        onClose={() => !working && setDeactivating(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setDeactivating(null)} disabled={working}>
              Cancel
            </button>
            <button type="button" className="btn btn-danger-soft" onClick={deactivate} disabled={working || hasProducts}>
              Deactivate
            </button>
          </>
        }
      >
        {hasProducts ? (
          <>
            <Alert variant="warning" className="mb-3">
              This category still has <strong>{deactivating.activeProductCount} active product(s)</strong>. Assign them to
              a new category before deactivating it, so they stay visible to customers.
            </Alert>
            <label htmlFor="target-category" className="form-label">
              Move products to
            </label>
            <div className="d-flex gap-2">
              <select
                id="target-category"
                className="form-select"
                value={targetCategoryId}
                onChange={(e) => setTargetCategoryId(e.target.value)}
                disabled={working}
              >
                <option value="">Choose an active category</option>
                {activeCategories.map((c) => (
                  <option key={c.categoryId} value={c.categoryId}>
                    {c.categoryName}
                  </option>
                ))}
              </select>
              <button type="button" className="btn btn-ink text-nowrap" onClick={reassign} disabled={!targetCategoryId || working}>
                {working && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                Move products
              </button>
            </div>
            {activeCategories.length === 0 && (
              <p className="small text-muted mt-2 mb-0">Create another active category first to move these products into.</p>
            )}
          </>
        ) : (
          <p className="text-muted mb-0">
            The category will be hidden from the store. It is a soft delete: the record stays in the database, so past
            orders and any inactive products keep their category, and you can reactivate it at any time.
          </p>
        )}
      </Modal>
    </>
  )
}
