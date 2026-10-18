import { useCallback, useEffect, useState } from 'react'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field } from '../../components/FormControls'
import { ActiveBadge, Alert, EmptyState, ErrorState, PageHeader, PageLoader } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDate, formatPrice } from '../../utils/format'

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
}

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
  if (f.minOrderAmount !== '' && Number(f.minOrderAmount) < 0) e.minOrderAmount = 'Cannot be negative'
  if (f.validFrom && f.validUntil && f.validUntil <= f.validFrom) e.validUntil = 'End must be after start'
  if (f.usageLimit !== '' && (!Number.isInteger(Number(f.usageLimit)) || Number(f.usageLimit) < 1)) e.usageLimit = 'Whole number, 1 or more'
  return e
}

export default function AdminCoupons() {
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [editor, setEditor] = useState(null)
  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)

  const load = useCallback(() => {
    setError(null)
    adminService
      .getCoupons({ page, size: 10 })
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
  }, [page])

  useEffect(() => {
    load()
  }, [load])

  const open = (coupon) => {
    setEditor(coupon ? { id: coupon.couponId } : {})
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
    }
    setSaving(true)
    try {
      if (editor.id) await adminService.updateCoupon(editor.id, payload)
      else await adminService.createCoupon(payload)
      toast.success(`Coupon ${payload.code} saved.`)
      setEditor(null)
      load()
    } catch (err) {
      const fieldErrors = getFieldErrors(err)
      if (err.response?.data?.code === 'DUPLICATE_COUPON') fieldErrors.code = getErrorMessage(err)
      setErrors(fieldErrors)
      if (!Object.keys(fieldErrors).length) setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const toggle = async (c) => {
    try {
      await adminService.setCouponActive(c.couponId, !c.active)
      toast.success(`${c.code} ${c.active ? 'paused' : 'activated'}.`)
      load()
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const now = new Date().toISOString()

  return (
    <>
      <PageHeader
        eyebrow="Promotions"
        title="Coupons"
        subtitle="Discount codes customers can apply at checkout. Each customer can use a code once."
        actions={
          <button type="button" className="btn btn-ink" onClick={() => open(null)}>
            <i className="bi bi-plus-lg me-2" />
            New coupon
          </button>
        }
      />
      <div className="ss-card overflow-hidden">
        {error ? (
          <ErrorState message={error} onRetry={load} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-ticket-perforated" title="No coupons yet">
            Create a code such as WELCOME10 to reward new customers.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Discount</th>
                  <th>Min. order</th>
                  <th>Valid</th>
                  <th>Used</th>
                  <th>Status</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((c) => {
                  const expired = c.validUntil && c.validUntil < now
                  return (
                    <tr key={c.couponId}>
                      <td>
                        <div className="ss-cell-title">{c.code}</div>
                        <div className="ss-cell-sub">{c.description}</div>
                      </td>
                      <td>{describe(c)}</td>
                      <td>{Number(c.minOrderAmount) > 0 ? formatPrice(c.minOrderAmount) : '—'}</td>
                      <td className="small">
                        {c.validFrom ? formatDate(c.validFrom) : 'Now'} – {c.validUntil ? formatDate(c.validUntil) : 'No end'}
                        {expired && <div className="text-danger">Expired</div>}
                      </td>
                      <td>
                        {c.usedCount}
                        {c.usageLimit ? ` / ${c.usageLimit}` : ''}
                      </td>
                      <td>
                        <ActiveBadge active={c.active} on="Active" off="Paused" />
                      </td>
                      <td className="text-end text-nowrap">
                        <button type="button" className="btn btn-ghost btn-sm me-2" onClick={() => open(c)}>
                          Edit
                        </button>
                        <button type="button" className={`btn btn-sm ${c.active ? 'btn-danger-soft' : 'btn-outline-ink'}`} onClick={() => toggle(c)}>
                          {c.active ? 'Pause' : 'Activate'}
                        </button>
                      </td>
                    </tr>
                  )
                })}
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
            <Field id="code" label="Code" value={form.code} maxLength={30} error={errors.code}
              onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))} />
          </div>
          <div className="col-md-7">
            <Field id="description" label="Description (shown at checkout)" value={form.description} maxLength={200} onChange={set('description')} error={errors.description} />
          </div>
          <div className="col-md-4">
            <Field id="discountType" as="select" label="Type" value={form.discountType} onChange={set('discountType')}>
              <option value="PERCENT">Percentage (%)</option>
              <option value="FLAT">Flat amount (₹)</option>
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
            <Field id="minOrderAmount" type="number" min="0" label="Minimum order (₹)" value={form.minOrderAmount} onChange={set('minOrderAmount')} error={errors.minOrderAmount} />
          </div>
          <div className="col-md-4">
            <Field id="validFrom" type="datetime-local" label="Starts (optional)" value={form.validFrom} onChange={set('validFrom')} error={errors.validFrom} />
          </div>
          <div className="col-md-4">
            <Field id="validUntil" type="datetime-local" label="Ends (optional)" value={form.validUntil} onChange={set('validUntil')} error={errors.validUntil} />
          </div>
          <div className="col-md-4">
            <Field id="usageLimit" type="number" min="1" label="Total uses (optional)" value={form.usageLimit} onChange={set('usageLimit')} error={errors.usageLimit} hint="Leave empty for unlimited" />
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
    </>
  )
}
