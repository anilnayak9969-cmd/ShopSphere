import { useCallback, useEffect, useState } from 'react'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import { Field } from '../../components/FormControls'
import { Alert, ErrorState, PageHeader, PageLoader } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'

const NUMBER_FIELDS = ['commissionPercent', 'freeShippingThreshold', 'shippingFee', 'codMaxAmount', 'returnWindowDays', 'lowStockThreshold']

function validate(f) {
  const e = {}
  const n = (k) => Number(f[k])
  NUMBER_FIELDS.forEach((k) => {
    if (f[k] === '' || Number.isNaN(n(k)) || n(k) < 0) e[k] = 'Enter a number, 0 or more'
  })
  if (!e.commissionPercent && n('commissionPercent') > 50) e.commissionPercent = 'Commission cannot exceed 50%'
  if (!e.shippingFee && n('shippingFee') > 1000) e.shippingFee = 'Shipping fee is too high'
  if (!e.codMaxAmount && n('codMaxAmount') < 1) e.codMaxAmount = 'Must be at least ₹1'
  if (!e.returnWindowDays && (!Number.isInteger(n('returnWindowDays')) || n('returnWindowDays') > 60)) e.returnWindowDays = 'Whole days, 0 to 60'
  if (!e.lowStockThreshold && (!Number.isInteger(n('lowStockThreshold')) || n('lowStockThreshold') > 1000)) e.lowStockThreshold = 'Whole number, 0 to 1000'
  return e
}

/** Business rules used across checkout, payouts and returns. */
export default function AdminSettings() {
  const toast = useToast()
  const [form, setForm] = useState(null)
  const [error, setError] = useState(null)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)

  const fill = (s) => setForm({ ...Object.fromEntries(NUMBER_FIELDS.map((k) => [k, String(s[k])])), codEnabled: s.codEnabled })

  const load = useCallback(() => {
    setError(null)
    adminService
      .getSettings()
      .then(fill)
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!form) return <PageLoader />

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const submit = async (event) => {
    event.preventDefault()
    const found = validate(form)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return
    setSaving(true)
    try {
      const payload = { ...Object.fromEntries(NUMBER_FIELDS.map((k) => [k, Number(form[k])])), codEnabled: form.codEnabled }
      fill(await adminService.updateSettings(payload))
      toast.success('Settings saved. They apply to new orders immediately.')
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader eyebrow="Platform" title="Settings" subtitle="Changes apply to new orders. Existing orders keep the fees and rules they were placed with." />
      {formError && (
        <Alert variant="danger" className="mb-4">
          {formError}
        </Alert>
      )}
      <form onSubmit={submit} noValidate style={{ maxWidth: 760 }}>
        <fieldset disabled={saving} className="ss-card ss-card-pad mb-4">
          <legend className="h5 mb-3">Shipping</legend>
          <div className="row g-3">
            <div className="col-md-6">
              <Field id="shippingFee" type="number" min="0" step="1" label="Shipping fee (₹)" value={form.shippingFee} onChange={set('shippingFee')} error={errors.shippingFee} />
            </div>
            <div className="col-md-6">
              <Field
                id="freeShippingThreshold"
                type="number"
                min="0"
                step="1"
                label="Free shipping from (₹)"
                value={form.freeShippingThreshold}
                onChange={set('freeShippingThreshold')}
                error={errors.freeShippingThreshold}
                hint="Order subtotal (after coupon) that qualifies for free delivery."
              />
            </div>
          </div>
        </fieldset>

        <fieldset disabled={saving} className="ss-card ss-card-pad mb-4">
          <legend className="h5 mb-3">Cash on delivery</legend>
          <div className="form-check form-switch mb-3">
            <input
              id="codEnabled"
              type="checkbox"
              role="switch"
              className="form-check-input"
              checked={form.codEnabled}
              onChange={(e) => setForm((f) => ({ ...f, codEnabled: e.target.checked }))}
            />
            <label htmlFor="codEnabled" className="form-check-label">
              Offer cash on delivery at checkout
            </label>
          </div>
          <Field
            id="codMaxAmount"
            type="number"
            min="1"
            step="1"
            label="Maximum order value for COD (₹)"
            value={form.codMaxAmount}
            onChange={set('codMaxAmount')}
            error={errors.codMaxAmount}
            disabled={!form.codEnabled}
          />
        </fieldset>

        <fieldset disabled={saving} className="ss-card ss-card-pad mb-4">
          <legend className="h5 mb-3">Sellers &amp; returns</legend>
          <div className="row g-3">
            <div className="col-md-4">
              <Field
                id="commissionPercent"
                type="number"
                min="0"
                max="50"
                step="0.5"
                label="Commission (%)"
                value={form.commissionPercent}
                onChange={set('commissionPercent')}
                error={errors.commissionPercent}
                hint="Charged on delivered items."
              />
            </div>
            <div className="col-md-4">
              <Field
                id="returnWindowDays"
                type="number"
                min="0"
                max="60"
                label="Return window (days)"
                value={form.returnWindowDays}
                onChange={set('returnWindowDays')}
                error={errors.returnWindowDays}
                hint="0 turns returns off."
              />
            </div>
            <div className="col-md-4">
              <Field
                id="lowStockThreshold"
                type="number"
                min="0"
                max="1000"
                label="Low-stock alert at"
                value={form.lowStockThreshold}
                onChange={set('lowStockThreshold')}
                error={errors.lowStockThreshold}
                hint="Units left."
              />
            </div>
          </div>
        </fieldset>

        <button type="submit" className="btn btn-ink btn-lg" disabled={saving}>
          {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
          Save settings
        </button>
      </form>
    </>
  )
}
