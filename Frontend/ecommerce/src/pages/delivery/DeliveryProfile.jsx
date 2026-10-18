import { useCallback, useEffect, useState } from 'react'
import deliveryService from '../../services/deliveryService'
import { useToast } from '../../context/ToastContext'
import { Field } from '../../components/FormControls'
import { Alert, ErrorState, PageHeader, PageLoader, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDate, labelize } from '../../utils/format'
import { PINCODE_RE } from '../../utils/validators'
import { normaliseVehicleNumber, VEHICLE_NUMBER_RE, VEHICLE_TYPES } from '../../utils/roles'

const STATUS_COPY = {
  PENDING: {
    variant: 'warning',
    icon: 'bi-hourglass-split',
    text: 'Your application is being reviewed. We will notify you here and by email once a decision is made, then invite you to onboarding (bring your driving licence and PAN).',
  },
  APPROVED: {
    variant: 'success',
    text: 'You are approved. Accept packed parcels from the Deliveries page; pickups in your service city are listed first.',
  },
  REJECTED: { variant: 'danger', text: 'Your application was not approved. Contact the ShopSphere team if you think this is a mistake.' },
  SUSPENDED: { variant: 'danger', text: 'Your partner account is suspended. Contact the ShopSphere team for details.' },
}

/** Delivery partner profile: application status plus vehicle and service-area details. */
export default function DeliveryProfile() {
  const toast = useToast()
  const [profile, setProfile] = useState(null)
  const [form, setForm] = useState(null)
  const [error, setError] = useState(null)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const fill = (p) => {
    setProfile(p)
    setForm({ vehicleType: p.vehicleType, vehicleNumber: p.vehicleNumber || '', serviceCity: p.serviceCity, servicePincode: p.servicePincode })
  }

  const load = useCallback(() => {
    setError(null)
    deliveryService
      .getProfile()
      .then(fill)
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!form) return <PageLoader />

  const copy = STATUS_COPY[profile.status] || STATUS_COPY.PENDING
  const needsNumber = form.vehicleType !== 'BICYCLE'

  const submit = async (event) => {
    event.preventDefault()
    const found = {}
    if (needsNumber && !VEHICLE_NUMBER_RE.test(normaliseVehicleNumber(form.vehicleNumber))) found.vehicleNumber = 'Enter a valid registration number, e.g. MH12AB1234'
    if (!form.serviceCity.trim()) found.serviceCity = 'City is required'
    if (!PINCODE_RE.test(form.servicePincode)) found.servicePincode = 'Enter a valid 6-digit PIN code'
    setErrors(found)
    if (Object.keys(found).length) return
    setSaving(true)
    try {
      fill(
        await deliveryService.updateProfile({
          vehicleType: form.vehicleType,
          vehicleNumber: needsNumber ? normaliseVehicleNumber(form.vehicleNumber) : null,
          serviceCity: form.serviceCity.trim(),
          servicePincode: form.servicePincode,
        }),
      )
      toast.success('Delivery profile saved.')
    } catch (err) {
      const fieldErrors = getFieldErrors(err)
      setErrors(Object.keys(fieldErrors).length ? fieldErrors : { form: getErrorMessage(err) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader eyebrow="Delivery partner" title="Profile" subtitle={`Applied on ${formatDate(profile.appliedAt)}`} actions={<StatusBadge status={profile.status} label={`Application ${labelize(profile.status).toLowerCase()}`} />} />
      <Alert variant={copy.variant} icon={copy.icon} className="mb-4">
        {copy.text}
      </Alert>

      <div className="row g-4">
        <div className="col-xl-7">
          <form onSubmit={submit} noValidate className="ss-card ss-card-pad">
            <h2 className="h5 mb-3">Vehicle &amp; service area</h2>
            {errors.form && (
              <Alert variant="danger" className="mb-3">
                {errors.form}
              </Alert>
            )}
            <fieldset disabled={saving} className="d-grid gap-3">
              <Field id="vehicleType" as="select" label="Vehicle" value={form.vehicleType} onChange={(e) => setForm((f) => ({ ...f, vehicleType: e.target.value }))}>
                {VEHICLE_TYPES.map((v) => (
                  <option key={v.value} value={v.value}>
                    {v.label}
                  </option>
                ))}
              </Field>
              {needsNumber && (
                <Field
                  id="vehicleNumber"
                  label="Registration number"
                  maxLength={15}
                  value={form.vehicleNumber}
                  onChange={(e) => setForm((f) => ({ ...f, vehicleNumber: e.target.value.toUpperCase() }))}
                  error={errors.vehicleNumber}
                />
              )}
              <div className="row g-3">
                <div className="col-md-7">
                  <Field id="serviceCity" label="City" maxLength={80} value={form.serviceCity} onChange={(e) => setForm((f) => ({ ...f, serviceCity: e.target.value }))} error={errors.serviceCity} />
                </div>
                <div className="col-md-5">
                  <Field
                    id="servicePincode"
                    label="Area PIN code"
                    inputMode="numeric"
                    maxLength={6}
                    value={form.servicePincode}
                    onChange={(e) => setForm((f) => ({ ...f, servicePincode: e.target.value.replace(/\D/g, '') }))}
                    error={errors.servicePincode}
                  />
                </div>
              </div>
              <div>
                <button type="submit" className="btn btn-ink">
                  {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                  Save details
                </button>
              </div>
            </fieldset>
          </form>
        </div>
        <div className="col-xl-5">
          <div className="ss-card ss-card-pad">
            <h2 className="h5 mb-3">Your account</h2>
            <dl className="small mb-0">
              <dt>Name</dt>
              <dd>{profile.fullName}</dd>
              <dt>Email</dt>
              <dd>{profile.email}</dd>
              <dt>Mobile</dt>
              <dd>{profile.phone}</dd>
              <dt>Vehicle</dt>
              <dd className="mb-0">
                {VEHICLE_TYPES.find((v) => v.value === profile.vehicleType)?.label}
                {profile.vehicleNumber && ` · ${profile.vehicleNumber}`}
              </dd>
            </dl>
          </div>
        </div>
      </div>
    </>
  )
}
