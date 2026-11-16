import { useCallback, useEffect, useState } from 'react'
import adminService from '../../services/adminService'
import categoryService from '../../services/categoryService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import Modal, { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field } from '../../components/FormControls'
import { Alert, EmptyState, ErrorState, PageHeader, PageLoader, Segment, StatCard } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDate, formatPrice } from '../../utils/format'

/** Coupon and Discount Management — dashboard of active and expired coupons; create, edit, deactivate, delete. */
const EMPTY = {
  code: '',
  description: '',
  discountType: 'PERCENT',
  discountValue: '',
  minOrderAmount: '0',
  maxDiscount: '',
  validFrom: '',
  validUntil: '',
  usageLimit: '',
  active: true,
  products: [],
  categoryIds: [],
}
const FILTERS = [
  { value: '', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'SCHEDULED', label: 'Scheduled' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'INACTIVE', label: 'Inactive' },
  { value: 'USED_UP', label: 'Used up' },
  { value: 'DELETED', label: 'Deleted' },
]
const STATUS_VARIANT = { ACTIVE: 'success', SCHEDULED: 'info', EXPIRED: 'danger', INACTIVE: 'muted', USED_UP: 'warning', DELETED: 'muted' }

/** "2026-10-16T10:00:00" <-> the value format of <input type="datetime-local"> */
const toInput = (v) => (v ? v.slice(0, 16) : '')
const fromInput = (v) => (v ? `${v}:00` : null)

function describe(c) {
  const value = c.discountType === 'PERCENT' ? `${Number(c.discountValue)}% off` : `${formatPrice(c.discountValue)} off`
  const cap = c.discountType === 'PERCENT' && c.maxDiscount ? ` (max ${formatPrice(c.maxDiscount)})` : ''
  return value + cap
}

function validate(f) {
  const e = {}
  if (!/^[A-Za-z0-9]{4,30}$/.test(f.code.trim())) e.code = 'Code must be 4-30 letters or numbers'
  const value = Number(f.discountValue)
  if (!f.discountValue || value < 1) e.discountValue = 'Discount must be at least 1'
  else if (f.discountType === 'PERCENT' && value > 90) e.discountValue = 'A percentage discount cannot exceed 90%'
  else if (f.discountType === 'FLAT' && Number(f.minOrderAmount) > 0 && value > Number(f.minOrderAmount)) e.discountValue = 'A fixed discount cannot be larger than the minimum order'
  if (f.minOrderAmount !== '' && Number(f.minOrderAmount) < 0) e.minOrderAmount = 'Cannot be negative'
  if (f.validFrom && f.validUntil && f.validUntil <= f.validFrom) e.validUntil = 'End must be after start'
  if (f.usageLimit !== '' && (!Number.isInteger(Number(f.usageLimit)) || Number(f.usageLimit) < 1)) e.usageLimit = 'Whole number, 1 or more'
  return e
}

/** Search box + chips for limiting a coupon to specific products. */
function ProductPicker({ value, onChange }) {
  const [search, setSearch] = useState('')
  const debounced = useDebounce(search.trim(), 300)
  const [matches, setMatches] = useState([])

  useEffect(() => {
    if (debounced.length < 2) return undefined
    let cancelled = false
    adminService
      .getProducts({ search: debounced, size: 6 })
      .then((page) => !cancelled && setMatches(page.content))
      .catch(() => !cancelled && setMatches([]))
    return () => {
      cancelled = true
    }
  }, [debounced])

  const chosen = new Set(value.map((p) => p.productId))
  const shown = debounced.length < 2 ? [] : matches.filter((p) => !chosen.has(p.productId))
  return (
    <div>
      <label htmlFor="coupon-product-search" className="form-label">
        Limit to products (optional)
      </label>
      {value.length > 0 && (
        <div className="d-flex flex-wrap gap-2 mb-2">
          {value.map((p) => (
            <span key={p.productId} className="ss-pill ss-pill-info d-inline-flex align-items-center gap-1">
              {p.productName}
              <button type="button" className="btn btn-link btn-sm p-0 lh-1" aria-label={`Remove ${p.productName}`} onClick={() => onChange(value.filter((x) => x.productId !== p.productId))}>
                <i className="bi bi-x" aria-hidden="true" />
              </button>
            </span>
          ))}
        </div>
      )}
      <input id="coupon-product-search" type="search" className="form-control" placeholder="Search products by name or SKU" value={search} onChange={(e) => setSearch(e.target.value)} />
      {shown.length > 0 && (
        <div className="list-group mt-1" role="listbox" aria-label="Matching products">
          {shown.map((p) => (
            <button
              type="button"
              key={p.productId}
              className="list-group-item list-group-item-action small d-flex justify-content-between"
              onClick={() => {
                onChange([...value, { productId: p.productId, productName: p.productName }])
                setSearch('')
              }}
            >
              <span>{p.productName}</span>
              <span className="text-muted">{p.categoryName}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export default function AdminCoupons() {
  const toast = useToast()
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const debounced = useDebounce(search.trim(), 300)
  const [summary, setSummary] = useState(null)
  const { result, error, reload, setPage } = usePagedList(
    (page) => adminService.getCoupons({ status, search: debounced, page, size: 10 }),
    [status, debounced],
  )
  const [categories, setCategories] = useState([])
  const [editor, setEditor] = useState(null)
  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(null)

  const loadSummary = useCallback(() => {
    adminService.getCouponSummary().then(setSummary).catch(() => setSummary(null))
  }, [])
  useEffect(() => {
    loadSummary()
    categoryService.getCategories().then(setCategories).catch(() => setCategories([]))
  }, [loadSummary])

  const refresh = () => {
    reload()
    loadSummary()
  }

  const open = (coupon) => {
    setEditor(coupon ? { id: coupon.couponId, used: coupon.usedCount > 0 } : {})
    setForm(
      coupon
        ? {
            code: coupon.code,
            description: coupon.description || '',
            discountType: coupon.discountType,
            discountValue: String(coupon.discountValue),
            minOrderAmount: String(coupon.minOrderAmount ?? 0),
            maxDiscount: coupon.maxDiscount != null ? String(coupon.maxDiscount) : '',
            validFrom: toInput(coupon.validFrom),
            validUntil: toInput(coupon.validUntil),
            usageLimit: coupon.usageLimit != null ? String(coupon.usageLimit) : '',
            active: coupon.active,
            products: coupon.products || [],
            categoryIds: (coupon.categories || []).map((c) => c.categoryId),
          }
        : EMPTY,
    )
    setErrors({})
    setFormError(null)
  }

  const save = async (event) => {
    event.preventDefault()
    const found = validate(form)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return
    const payload = {
      code: form.code.trim().toUpperCase(),
      description: form.description.trim() || null,
      discountType: form.discountType,
      discountValue: Number(form.discountValue),
      minOrderAmount: form.minOrderAmount === '' ? 0 : Number(form.minOrderAmount),
      maxDiscount: form.discountType === 'PERCENT' && form.maxDiscount !== '' ? Number(form.maxDiscount) : null,
      validFrom: fromInput(form.validFrom),
      validUntil: fromInput(form.validUntil),
      usageLimit: form.usageLimit === '' ? null : Number(form.usageLimit),
      active: form.active,
      productIds: form.products.map((p) => p.productId),
      categoryIds: form.categoryIds,
    }
    setSaving(true)
    try {
      if (editor.id) await adminService.updateCoupon(editor.id, payload)
      else await adminService.createCoupon(payload)
      toast.success(`Coupon ${payload.code} ${editor.id ? 'updated' : 'created'}.`)
      setEditor(null)
      refresh()
    } catch (err) {
      const fieldErrors = getFieldErrors(err)
      if (err.response?.data?.code === 'DUPLICATE_COUPON') fieldErrors.code = getErrorMessage(err)
      setErrors(fieldErrors)
      if (!Object.keys(fieldErrors).length) setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const run = async (action, message) => {
    try {
      await action()
      toast.success(message)
      refresh()
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  const remove = async () => {
    setSaving(true)
    try {
      await adminService.deleteCoupon(deleting.couponId)
      toast.success(`${deleting.code} deleted. It no longer works at checkout; find it under Deleted to restore it.`)
      setDeleting(null)
      refresh()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const toggleCategory = (id) =>
    setForm((f) => ({ ...f, categoryIds: f.categoryIds.includes(id) ? f.categoryIds.filter((x) => x !== id) : [...f.categoryIds, id] }))
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const parents = categories.filter((c) => c.parentCategoryId == null)

  return (
    <>
      <PageHeader
        eyebrow="Promotions"
        title="Coupons & discounts"
        subtitle="Percentage or fixed-amount codes with validity dates, usage limits and optional product, category or order-total limits. Each customer can use a code once."
        actions={
          <button type="button" className="btn btn-ink" onClick={() => open(null)}>
            <i className="bi bi-plus-lg me-2" />
            New coupon
          </button>
        }
      />

      <div className="row g-3 mb-4">
        <div className="col-6 col-lg-3">
          <StatCard label="Active" value={summary?.active ?? '—'} icon="bi-ticket-perforated" foot={summary && `${summary.scheduled} scheduled to start`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Expired" value={summary?.expired ?? '—'} icon="bi-calendar-x" foot={summary && `${summary.usedUp} used up`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Inactive / deleted" value={summary ? summary.inactive + summary.deleted : '—'} icon="bi-pause-circle" foot={summary && `${summary.inactive} inactive · ${summary.deleted} deleted`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Discount given" value={summary ? formatPrice(summary.discountGiven) : '—'} icon="bi-cash-coin" dark foot={summary && `${summary.redemptions} order(s) used a coupon (cancelled excluded)`} />
        </div>
      </div>

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar flex-wrap">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input type="search" className="form-control form-control-sm" placeholder="Code or description" aria-label="Search coupons" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <Segment label="Coupon status" options={FILTERS} value={status} onChange={setStatus} />
        </div>
        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-ticket-perforated" title="No coupons here">
            {status || debounced ? 'No coupon matches these filters.' : 'Create a code such as WELCOME10 to reward new customers.'}
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table align-middle">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Discount</th>
                  <th>Applies to</th>
                  <th>Valid</th>
                  <th>Used</th>
                  <th>Status</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((c) => (
                  <tr key={c.couponId}>
                    <td>
                      <div className="ss-cell-title">{c.code}</div>
                      <div className="ss-cell-sub" style={{ maxWidth: 220 }}>{c.description}</div>
                    </td>
                    <td className="small">
                      <div className="fw-semibold">{describe(c)}</div>
                      <div className="text-muted">{Number(c.minOrderAmount) > 0 ? `Min. order ${formatPrice(c.minOrderAmount)}` : 'No minimum'}</div>
                    </td>
                    <td className="small" style={{ maxWidth: 200 }}>{c.appliesTo}</td>
                    <td className="small text-nowrap">
                      {c.validFrom ? formatDate(c.validFrom) : 'Now'} – {c.validUntil ? formatDate(c.validUntil) : 'No end'}
                    </td>
                    <td className="small text-nowrap">
                      {c.usedCount}
                      {c.usageLimit ? ` / ${c.usageLimit}` : ''}
                      {Number(c.discountGiven) > 0 && <div className="text-muted">{formatPrice(c.discountGiven)} off</div>}
                    </td>
                    <td>
                      <span className={`ss-pill ss-pill-${STATUS_VARIANT[c.status] || 'muted'}`}>{c.statusLabel}</span>
                    </td>
                    <td className="text-end text-nowrap">
                      {c.status === 'DELETED' ? (
                        <button type="button" className="btn btn-sm btn-outline-ink" onClick={() => run(() => adminService.restoreCoupon(c.couponId), `${c.code} restored as inactive.`)}>
                          <i className="bi bi-arrow-counterclockwise me-1" aria-hidden="true" />
                          Restore
                        </button>
                      ) : (
                        <>
                          <button type="button" className="btn btn-ghost btn-sm me-1" onClick={() => open(c)}>
                            Edit
                          </button>
                          <button type="button" className="btn btn-sm btn-outline-ink me-1" onClick={() => run(() => adminService.setCouponActive(c.couponId, !c.active), `${c.code} ${c.active ? 'deactivated' : 'activated'}.`)}>
                            {c.active ? 'Deactivate' : 'Activate'}
                          </button>
                          <button type="button" className="btn btn-sm btn-danger-soft" aria-label={`Delete ${c.code}`} onClick={() => setDeleting(c)}>
                            <i className="bi bi-trash" aria-hidden="true" />
                          </button>
                        </>
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

      <Modal
        open={Boolean(editor)}
        size="lg"
        title={editor?.id ? 'Edit coupon' : 'New coupon'}
        onClose={() => !saving && setEditor(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setEditor(null)} disabled={saving}>
              Cancel
            </button>
            <button type="submit" form="coupon-form" className="btn btn-ink" disabled={saving}>
              {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              Save coupon
            </button>
          </>
        }
      >
        {formError && (
          <Alert variant="danger" className="mb-3">
            {formError}
          </Alert>
        )}
        <form id="coupon-form" onSubmit={save} noValidate className="row g-3">
          <div className="col-md-5">
            <Field id="code" label="Code" value={form.code} maxLength={30} error={errors.code} disabled={editor?.used}
              hint={editor?.used ? 'Already used by customers, so the code cannot change' : 'Customers type this at checkout'}
              onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))} />
          </div>
          <div className="col-md-7">
            <Field id="description" label="Description (shown at checkout)" value={form.description} maxLength={200} onChange={set('description')} error={errors.description} />
          </div>
          <div className="col-md-4">
            <Field id="discountType" as="select" label="Discount type" value={form.discountType} onChange={set('discountType')}>
              <option value="PERCENT">Percentage (%)</option>
              <option value="FLAT">Fixed amount (₹)</option>
            </Field>
          </div>
          <div className="col-md-4">
            <Field id="discountValue" type="number" min="1" label={form.discountType === 'PERCENT' ? 'Discount (%)' : 'Discount (₹)'} value={form.discountValue} onChange={set('discountValue')} error={errors.discountValue} />
          </div>
          {form.discountType === 'PERCENT' && (
            <div className="col-md-4">
              <Field id="maxDiscount" type="number" min="1" label="Max discount ₹ (optional)" value={form.maxDiscount} onChange={set('maxDiscount')} error={errors.maxDiscount} />
            </div>
          )}
          <div className="col-md-4">
            <Field id="validFrom" type="datetime-local" label="Valid from (optional)" value={form.validFrom} onChange={set('validFrom')} error={errors.validFrom} />
          </div>
          <div className="col-md-4">
            <Field id="validUntil" type="datetime-local" label="Expires (optional)" value={form.validUntil} onChange={set('validUntil')} error={errors.validUntil} />
          </div>
          <div className="col-md-4">
            <Field id="usageLimit" type="number" min="1" label="Usage limit (optional)" value={form.usageLimit} onChange={set('usageLimit')} error={errors.usageLimit} hint="Total uses; empty = unlimited" />
          </div>

          <div className="col-12">
            <h3 className="h6 mb-1 mt-2">Limits</h3>
            <p className="small text-muted mb-2">Leave products and categories empty for a coupon on the whole order. When you choose some, the discount is worked out only on those items.</p>
          </div>
          <div className="col-md-4">
            <Field id="minOrderAmount" type="number" min="0" label="Minimum order total (₹)" value={form.minOrderAmount} onChange={set('minOrderAmount')} error={errors.minOrderAmount} />
          </div>
          <div className="col-md-8">
            <ProductPicker value={form.products} onChange={(products) => setForm((f) => ({ ...f, products }))} />
          </div>
          <div className="col-12">
            <span className="form-label d-block">Limit to categories (optional, includes subcategories)</span>
            <div className="d-flex flex-wrap gap-2" role="group" aria-label="Categories">
              {parents.map((c) => (
                <button type="button" key={c.categoryId} aria-pressed={form.categoryIds.includes(c.categoryId)}
                  className={`btn btn-sm ${form.categoryIds.includes(c.categoryId) ? 'btn-ink' : 'btn-outline-ink'}`} onClick={() => toggleCategory(c.categoryId)}>
                  {form.categoryIds.includes(c.categoryId) && <i className="bi bi-check2 me-1" aria-hidden="true" />}
                  {c.categoryName}
                </button>
              ))}
            </div>
          </div>
          <div className="col-12">
            <div className="form-check">
              <input id="coupon-active" type="checkbox" className="form-check-input" checked={form.active} onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))} />
              <label htmlFor="coupon-active" className="form-check-label">
                Active — customers can use this code
              </label>
            </div>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(deleting)}
        title={`Delete ${deleting?.code}?`}
        confirmLabel="Delete coupon"
        danger
        busy={saving}
        onConfirm={remove}
        onCancel={() => setDeleting(null)}
      >
        The code stops working at checkout and leaves the dashboard. This is a soft delete: the coupon and the orders
        that used it are kept, and you can restore it from the Deleted filter.
      </ConfirmDialog>
    </>
  )
}
