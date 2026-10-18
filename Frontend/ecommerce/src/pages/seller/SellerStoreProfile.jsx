import { useCallback, useEffect, useState } from 'react'
import sellerService from '../../services/sellerService'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import { Field } from '../../components/FormControls'
import ImageUpload from '../../components/ImageUpload'
import { Alert, ErrorState, PageHeader, PageLoader, SafeImage, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDate } from '../../utils/format'
import { IMAGE_URL_RE } from '../../utils/validators'
import { BUSINESS_TYPES } from '../../utils/roles'
import SellerStatusBanner from './SellerStatusBanner'

const FIELDS = ['storeName', 'description', 'logoUrl', 'supportEmail', 'supportPhone', 'gstin', 'businessAddress', 'city', 'state', 'pincode']

const GSTIN_RE = /^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/

function validate(f) {
  const e = {}
  if (f.storeName.trim().length < 2) e.storeName = 'Store name must be 2-120 characters'
  if (f.logoUrl.trim() && !IMAGE_URL_RE.test(f.logoUrl.trim())) e.logoUrl = 'Upload an image or enter a link starting with http(s)://'
  if (f.supportEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.supportEmail.trim())) e.supportEmail = 'Enter a valid email'
  if (f.supportPhone.trim() && !/^[6-9]\d{9}$/.test(f.supportPhone.trim())) e.supportPhone = 'Enter a valid 10-digit mobile number'
  if (f.gstin.trim() && !GSTIN_RE.test(f.gstin.trim().toUpperCase())) e.gstin = 'Enter a valid 15-character GSTIN (e.g. 27ABCDE1234F1Z5)'
  if (f.pincode.trim() && !/^[1-9]\d{5}$/.test(f.pincode.trim())) e.pincode = 'Enter a valid 6-digit PIN code'
  return e
}

export default function SellerStoreProfile() {
  const { refreshUser } = useAuth()
  const toast = useToast()
  const [profile, setProfile] = useState(null)
  const [form, setForm] = useState(null)
  const [error, setError] = useState(null)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)

  const fill = (p) => {
    setProfile(p)
    setForm(Object.fromEntries(FIELDS.map((k) => [k, p[k] || ''])))
  }

  const load = useCallback(() => {
    setError(null)
    sellerService
      .getProfile()
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
    const payload = Object.fromEntries(FIELDS.map((k) => [k, form[k].trim()]))
    payload.gstin = payload.gstin.toUpperCase()
    setSaving(true)
    try {
      fill(await sellerService.updateProfile(payload))
      refreshUser().catch(() => {})
      toast.success('Store profile saved.')
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader eyebrow="Store" title="Store profile" subtitle="Tell customers who you are. Your store name appears on every product you sell." />
      <SellerStatusBanner status={profile.sellerStatus} />
      {formError && (
        <Alert variant="danger" className="mb-4">
          {formError}
        </Alert>
      )}
      <div className="row g-4">
        <div className="col-xl-8">
          <form onSubmit={submit} noValidate>
            <fieldset disabled={saving} className="ss-card ss-card-pad mb-4">
              <legend className="h5 mb-3">Storefront</legend>
              <div className="row g-3">
                <div className="col-12">
                  <Field id="storeName" label="Store name" maxLength={120} value={form.storeName} onChange={set('storeName')} error={errors.storeName} />
                </div>
                <div className="col-12">
                  <Field
                    id="description"
                    as="textarea"
                    rows={4}
                    label="About your store"
                    maxLength={1000}
                    value={form.description}
                    onChange={set('description')}
                    error={errors.description}
                    hint={`${form.description.length}/1000 characters`}
                  />
                </div>
                <div className="col-12">
                  <ImageUpload id="logoUrl" label="Store logo" value={form.logoUrl} onChange={(v) => setForm((f) => ({ ...f, logoUrl: v }))} error={errors.logoUrl} />
                </div>
              </div>
            </fieldset>

            <fieldset disabled={saving} className="ss-card ss-card-pad mb-4">
              <legend className="h5 mb-3">Customer support</legend>
              <div className="row g-3">
                <div className="col-md-6">
                  <Field id="supportEmail" type="email" label="Support email" maxLength={150} value={form.supportEmail} onChange={set('supportEmail')} error={errors.supportEmail} />
                </div>
                <div className="col-md-6">
                  <Field id="supportPhone" type="tel" inputMode="numeric" label="Support phone" maxLength={10} value={form.supportPhone} onChange={set('supportPhone')} error={errors.supportPhone} />
                </div>
              </div>
            </fieldset>

            <fieldset disabled={saving} className="ss-card ss-card-pad mb-4">
              <legend className="h5 mb-3">Business details</legend>
              <div className="row g-3">
                <div className="col-md-6">
                  <Field id="gstin" label="GSTIN (optional)" maxLength={15} value={form.gstin} onChange={set('gstin')} error={errors.gstin} style={{ textTransform: 'uppercase' }} />
                </div>
                <div className="col-12">
                  <Field id="businessAddress" label="Business address" maxLength={255} value={form.businessAddress} onChange={set('businessAddress')} error={errors.businessAddress} />
                </div>
                <div className="col-md-4">
                  <Field id="city" label="City" maxLength={80} value={form.city} onChange={set('city')} error={errors.city} />
                </div>
                <div className="col-md-4">
                  <Field id="state" label="State" maxLength={80} value={form.state} onChange={set('state')} error={errors.state} />
                </div>
                <div className="col-md-4">
                  <Field id="pincode" inputMode="numeric" label="PIN code" maxLength={6} value={form.pincode} onChange={set('pincode')} error={errors.pincode} />
                </div>
              </div>
            </fieldset>

            <button type="submit" className="btn btn-ink btn-lg" disabled={saving}>
              {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              Save profile
            </button>
          </form>
        </div>

        <div className="col-xl-4">
          <div className="ss-card ss-card-pad" style={{ position: 'sticky', top: 90 }}>
            <div className="d-flex align-items-center gap-3 mb-3">
              <div className="ss-thumb" style={{ width: 64, height: 64 }}>
                <SafeImage key={form.logoUrl} src={IMAGE_URL_RE.test(form.logoUrl.trim()) ? form.logoUrl.trim() : null} alt="Store logo" />
              </div>
              <div className="min-w-0">
                <div className="fw-bold text-truncate">{form.storeName || 'Your store'}</div>
                <StatusBadge status={profile.sellerStatus} />
              </div>
            </div>
            <dl className="small mb-0">
              <dt>Owner</dt>
              <dd>
                {profile.ownerName} · {profile.ownerEmail}
              </dd>
              {profile.businessType && (
                <>
                  <dt>Business type</dt>
                  <dd>{BUSINESS_TYPES.find((b) => b.value === profile.businessType)?.label}</dd>
                </>
              )}
              <dt>Selling since</dt>
              <dd className="mb-0">{formatDate(profile.memberSince)}</dd>
            </dl>
          </div>
        </div>
      </div>
    </>
  )
}
