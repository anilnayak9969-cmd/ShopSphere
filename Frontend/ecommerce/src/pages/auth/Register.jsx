import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import authService from '../../services/authService'
import { useToast } from '../../context/ToastContext'
import { Field, PasswordInput } from '../../components/FormControls'
import { INDIAN_STATES } from '../../components/AddressForm'
import { Alert } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { EMAIL_RE, PASSWORD_MESSAGE, PASSWORD_RE, PHONE_RE, PINCODE_RE } from '../../utils/validators'
import {
  BUSINESS_TYPES,
  GSTIN_RE,
  normaliseVehicleNumber,
  REGISTER_ROLES,
  ROLE_INFO,
  VEHICLE_NUMBER_RE,
  VEHICLE_TYPES,
} from '../../utils/roles'
import AuthShell from './AuthShell'

const EMPTY = {
  fullName: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  acceptTerms: false,
  // seller
  storeName: '',
  businessType: '',
  gstin: '',
  pickupAddress: '',
  pickupCity: '',
  pickupState: '',
  pickupPincode: '',
  // delivery partner
  vehicleType: '',
  vehicleNumber: '',
  serviceCity: '',
  servicePincode: '',
}

function validate(role, f) {
  const e = {}
  if (f.fullName.trim().length < 2) e.fullName = 'Enter your full name'
  if (!EMAIL_RE.test(f.email.trim())) e.email = 'Enter a valid email address'
  if (!PHONE_RE.test(f.phone)) e.phone = 'Enter a valid 10-digit mobile number'
  if (role === 'SELLER') {
    if (f.storeName.trim().length < 2) e.storeName = 'Enter your store name'
    if (!f.businessType) e.businessType = 'Choose your business type'
    if (f.gstin.trim() && !GSTIN_RE.test(f.gstin.trim().toUpperCase())) e.gstin = 'Enter a valid 15-character GSTIN (e.g. 27ABCDE1234F1Z5)'
    if (!f.pickupAddress.trim()) e.pickupAddress = 'Enter the address couriers collect from'
    if (!f.pickupCity.trim()) e.pickupCity = 'City is required'
    if (!f.pickupState) e.pickupState = 'Choose a state'
    if (!PINCODE_RE.test(f.pickupPincode)) e.pickupPincode = 'Enter a valid 6-digit PIN code'
  }
  if (role === 'DELIVERY_PARTNER') {
    if (!f.vehicleType) e.vehicleType = 'Choose your vehicle'
    if (f.vehicleType && f.vehicleType !== 'BICYCLE' && !VEHICLE_NUMBER_RE.test(normaliseVehicleNumber(f.vehicleNumber))) {
      e.vehicleNumber = 'Enter a valid registration number, e.g. MH12AB1234'
    }
    if (!f.serviceCity.trim()) e.serviceCity = 'City is required'
    if (!PINCODE_RE.test(f.servicePincode)) e.servicePincode = 'Enter a valid 6-digit PIN code'
  }
  if (!PASSWORD_RE.test(f.password)) e.password = PASSWORD_MESSAGE
  if (f.confirmPassword !== f.password) e.confirmPassword = 'Passwords do not match'
  if (!f.acceptTerms) e.acceptTerms = 'Please accept the terms and privacy policy'
  return e
}

/** Numbered heading for one block of the form. */
function Section({ n, title, hint, children }) {
  return (
    <fieldset className="ss-reg-section">
      <legend>
        <span className="ss-reg-num">{n}</span>
        {title}
      </legend>
      {hint && <p className="small text-muted mb-3">{hint}</p>}
      <div className="d-grid gap-3">{children}</div>
    </fieldset>
  )
}

export default function Register() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const toast = useToast()
  const initialRole = REGISTER_ROLES.includes(params.get('role')) ? params.get('role') : 'CUSTOMER'
  const [role, setRole] = useState(initialRole)
  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const info = ROLE_INFO[role]
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const choose = (field, value) => setForm((f) => ({ ...f, [field]: value }))

  const submit = async (event) => {
    event.preventDefault()
    const found = validate(role, form)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) {
      // Bring the first problem into view on long forms.
      document.getElementById(Object.keys(found)[0])?.focus()
      return
    }

    const seller = role === 'SELLER'
    const partner = role === 'DELIVERY_PARTNER'
    setSubmitting(true)
    try {
      const response = await authService.register({
        role,
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone,
        password: form.password,
        acceptTerms: form.acceptTerms,
        storeName: seller ? form.storeName.trim() : null,
        businessType: seller ? form.businessType : null,
        gstin: seller ? form.gstin.trim().toUpperCase() : null,
        pickupAddress: seller ? form.pickupAddress.trim() : null,
        pickupCity: seller ? form.pickupCity.trim() : null,
        pickupState: seller ? form.pickupState : null,
        pickupPincode: seller ? form.pickupPincode : null,
        vehicleType: partner ? form.vehicleType : null,
        vehicleNumber: partner && form.vehicleType !== 'BICYCLE' ? normaliseVehicleNumber(form.vehicleNumber) : null,
        serviceCity: partner ? form.serviceCity.trim() : null,
        servicePincode: partner ? form.servicePincode : null,
      })
      toast.success(response.message)
      navigate('/verify-email', { state: { email: form.email.trim().toLowerCase() } })
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  let n = 1
  return (
    <AuthShell
      wide
      image={info.image}
      asideTitle={info.asideTitle}
      asideText={info.asideText}
      points={info.points}
      topLink={
        <>
          Already registered? <Link to="/login">Sign in</Link>
        </>
      }
    >
      <ol className="ss-reg-progress" aria-label="Sign-up steps">
        <li className="done">Account type</li>
        <li className="current" aria-current="step">
          Your details
        </li>
        <li>Verify email</li>
      </ol>

      <span className="ss-eyebrow">Create account</span>
      <h1>{role === 'SELLER' ? 'Apply to sell' : role === 'DELIVERY_PARTNER' ? 'Become a delivery partner' : 'Join ShopSphere'}</h1>
      <p className="text-muted mb-4">Choose how you want to use ShopSphere. We verify every email with a one-time code.</p>

      {formError && (
        <Alert variant="danger" className="mb-3">
          {formError}
        </Alert>
      )}

      <div className="ss-role-cards mb-4" role="radiogroup" aria-label="Account type">
        {REGISTER_ROLES.map((r) => {
          const ri = ROLE_INFO[r]
          const selected = r === role
          return (
            <button
              type="button"
              key={r}
              role="radio"
              aria-checked={selected}
              className={`ss-role-card ${selected ? 'selected' : ''}`}
              onClick={() => {
                setRole(r)
                setErrors({})
                setFormError(null)
              }}
            >
              <span className="ss-role-card-icon">
                <i className={`bi ${ri.icon}`} aria-hidden="true" />
              </span>
              <strong>{ri.title}</strong>
              <small>{ri.tagline}</small>
              <i className="bi bi-check-circle-fill ss-role-card-check" aria-hidden="true" />
            </button>
          )
        })}
      </div>

      <div className="ss-role-needs mb-4" aria-live="polite">
        <i className="bi bi-card-checklist" aria-hidden="true" />
        <span>
          <strong>{ROLE_INFO[role].label} sign-up needs:</strong> {info.needs.join(' · ')}
        </span>
      </div>

      <form onSubmit={submit} noValidate className="d-grid gap-4" key={role}>
        <Section n={n++} title="Your details">
          <Field id="fullName" label="Full name" autoComplete="name" value={form.fullName} onChange={set('fullName')} error={errors.fullName} />
          <div className="row g-3">
            <div className="col-md-7">
              <Field id="email" label="Email" type="email" autoComplete="email" value={form.email} onChange={set('email')} error={errors.email} />
            </div>
            <div className="col-md-5">
              <Field
                id="phone"
                label="Mobile number"
                inputMode="numeric"
                maxLength={10}
                autoComplete="tel-national"
                value={form.phone}
                onChange={(e) => choose('phone', e.target.value.replace(/\D/g, ''))}
                error={errors.phone}
              />
            </div>
          </div>
        </Section>

        {role === 'SELLER' && (
          <Section n={n++} title="Business details" hint="Customers see your store name. The pickup address is where couriers collect your orders.">
            <Field id="storeName" label="Store name" maxLength={120} value={form.storeName} onChange={set('storeName')} error={errors.storeName} />
            <div>
              <span className="form-label d-block" id="businessType-label">
                Business type
              </span>
              <div className="ss-choice-grid" role="radiogroup" aria-labelledby="businessType-label" id="businessType" tabIndex={-1}>
                {BUSINESS_TYPES.map((b) => (
                  <button
                    type="button"
                    key={b.value}
                    role="radio"
                    aria-checked={form.businessType === b.value}
                    className={`ss-choice ${form.businessType === b.value ? 'selected' : ''}`}
                    onClick={() => choose('businessType', b.value)}
                  >
                    <strong>{b.label}</strong>
                    <small>{b.hint}</small>
                  </button>
                ))}
              </div>
              {errors.businessType && <div className="invalid-feedback d-block">{errors.businessType}</div>}
            </div>
            <Field
              id="gstin"
              label="GSTIN (optional)"
              maxLength={15}
              value={form.gstin}
              onChange={(e) => choose('gstin', e.target.value.toUpperCase())}
              error={errors.gstin}
              hint="Needed to sell taxable goods. You can add it later from your store profile."
            />
            <Field id="pickupAddress" label="Pickup address" autoComplete="street-address" maxLength={255} value={form.pickupAddress} onChange={set('pickupAddress')} error={errors.pickupAddress} />
            <div className="row g-3">
              <div className="col-md-4">
                <Field id="pickupCity" label="City" autoComplete="address-level2" maxLength={80} value={form.pickupCity} onChange={set('pickupCity')} error={errors.pickupCity} />
              </div>
              <div className="col-md-5">
                <Field id="pickupState" as="select" label="State" value={form.pickupState} onChange={set('pickupState')} error={errors.pickupState}>
                  <option value="">Select state</option>
                  {INDIAN_STATES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </Field>
              </div>
              <div className="col-md-3">
                <Field
                  id="pickupPincode"
                  label="PIN code"
                  inputMode="numeric"
                  maxLength={6}
                  autoComplete="postal-code"
                  value={form.pickupPincode}
                  onChange={(e) => choose('pickupPincode', e.target.value.replace(/\D/g, ''))}
                  error={errors.pickupPincode}
                />
              </div>
            </div>
          </Section>
        )}

        {role === 'DELIVERY_PARTNER' && (
          <Section n={n++} title="Vehicle & service area" hint="Your driving licence and PAN are checked in person at onboarding, so no documents are uploaded here.">
            <div>
              <span className="form-label d-block" id="vehicleType-label">
                Vehicle
              </span>
              <div className="ss-choice-grid ss-choice-grid-4" role="radiogroup" aria-labelledby="vehicleType-label" id="vehicleType" tabIndex={-1}>
                {VEHICLE_TYPES.map((v) => (
                  <button
                    type="button"
                    key={v.value}
                    role="radio"
                    aria-checked={form.vehicleType === v.value}
                    className={`ss-choice ss-choice-icon ${form.vehicleType === v.value ? 'selected' : ''}`}
                    onClick={() => choose('vehicleType', v.value)}
                  >
                    <i className={`bi ${v.icon}`} aria-hidden="true" />
                    <strong>{v.label}</strong>
                  </button>
                ))}
              </div>
              {errors.vehicleType && <div className="invalid-feedback d-block">{errors.vehicleType}</div>}
            </div>
            {form.vehicleType && form.vehicleType !== 'BICYCLE' && (
              <Field
                id="vehicleNumber"
                label="Vehicle registration number"
                placeholder="MH12AB1234"
                maxLength={15}
                value={form.vehicleNumber}
                onChange={(e) => choose('vehicleNumber', e.target.value.toUpperCase())}
                error={errors.vehicleNumber}
              />
            )}
            <div className="row g-3">
              <div className="col-md-7">
                <Field id="serviceCity" label="City you want to deliver in" maxLength={80} value={form.serviceCity} onChange={set('serviceCity')} error={errors.serviceCity} />
              </div>
              <div className="col-md-5">
                <Field
                  id="servicePincode"
                  label="Area PIN code"
                  inputMode="numeric"
                  maxLength={6}
                  value={form.servicePincode}
                  onChange={(e) => choose('servicePincode', e.target.value.replace(/\D/g, ''))}
                  error={errors.servicePincode}
                />
              </div>
            </div>
          </Section>
        )}

        <Section n={n++} title="Secure your account">
          <PasswordInput id="password" label="Password" autoComplete="new-password" value={form.password} onChange={set('password')} error={errors.password} showRules />
          <PasswordInput
            id="confirmPassword"
            label="Confirm password"
            autoComplete="new-password"
            value={form.confirmPassword}
            onChange={set('confirmPassword')}
            error={errors.confirmPassword}
          />
          <div>
            <div className="form-check">
              <input
                id="acceptTerms"
                type="checkbox"
                className={`form-check-input ${errors.acceptTerms ? 'is-invalid' : ''}`}
                checked={form.acceptTerms}
                onChange={(e) => choose('acceptTerms', e.target.checked)}
              />
              <label htmlFor="acceptTerms" className="form-check-label small">
                I agree to the{' '}
                <Link to="/policies/terms" target="_blank">
                  Terms of use
                </Link>{' '}
                and{' '}
                <Link to="/policies/privacy" target="_blank">
                  Privacy policy
                </Link>
                {role === 'SELLER' && ', including the seller commission on delivered orders'}.
              </label>
            </div>
            {errors.acceptTerms && <div className="invalid-feedback d-block">{errors.acceptTerms}</div>}
          </div>
        </Section>

        <div>
          <button type="submit" className="btn btn-ink btn-lg w-100" disabled={submitting}>
            {submitting && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
            {info.submit}
          </button>
          <p className="small text-muted mb-0 mt-2 text-center">
            Next, we email you a 6-digit code to verify your address.
            {role !== 'CUSTOMER' && ' Your application is then reviewed by our team.'}
          </p>
        </div>
      </form>
    </AuthShell>
  )
}
