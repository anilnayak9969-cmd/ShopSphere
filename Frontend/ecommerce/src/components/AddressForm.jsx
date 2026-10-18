import { Field } from './FormControls'
import { PHONE_RE, PINCODE_RE } from '../utils/validators'

export const EMPTY_ADDRESS = {
  label: '',
  fullName: '',
  phone: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  pincode: '',
}

export const INDIAN_STATES = [
  'Andaman and Nicobar Islands', 'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chandigarh',
  'Chhattisgarh', 'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Goa', 'Gujarat', 'Haryana',
  'Himachal Pradesh', 'Jammu and Kashmir', 'Jharkhand', 'Karnataka', 'Kerala', 'Ladakh', 'Lakshadweep',
  'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Puducherry',
  'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh', 'Uttarakhand',
  'West Bengal',
]

/** Same rules as the backend's AddressRequest / CheckoutRequest. */
export function validateAddress(a) {
  const errors = {}
  if (!a.fullName.trim()) errors.fullName = 'Full name is required'
  if (!PHONE_RE.test(a.phone)) errors.phone = 'Enter a valid 10-digit mobile number'
  if (!a.addressLine1.trim()) errors.addressLine1 = 'Address is required'
  if (!a.city.trim()) errors.city = 'City is required'
  if (!a.state.trim()) errors.state = 'State is required'
  if (!PINCODE_RE.test(a.pincode)) errors.pincode = 'Enter a valid 6-digit PIN code'
  return errors
}

/** Controlled address fields. `idPrefix` keeps ids unique when two forms are on one page. */
export default function AddressForm({ value, onChange, errors = {}, showLabel = true, idPrefix = 'addr' }) {
  const set = (field) => (e) => onChange({ ...value, [field]: e.target.value })
  const id = (name) => `${idPrefix}-${name}`
  return (
    <div className="row g-3">
      <div className="col-md-6">
        <Field id={id('fullName')} label="Full name" value={value.fullName} onChange={set('fullName')} error={errors.fullName} autoComplete="name" maxLength={100} />
      </div>
      <div className="col-md-6">
        <Field id={id('phone')} label="Mobile number" value={value.phone} onChange={set('phone')} error={errors.phone} inputMode="numeric" maxLength={10} autoComplete="tel-national" />
      </div>
      <div className="col-12">
        <Field id={id('addressLine1')} label="Address" placeholder="House no., building, street" value={value.addressLine1} onChange={set('addressLine1')} error={errors.addressLine1} autoComplete="address-line1" maxLength={200} />
      </div>
      <div className="col-12">
        <Field id={id('addressLine2')} label="Landmark / area (optional)" value={value.addressLine2} onChange={set('addressLine2')} error={errors.addressLine2} autoComplete="address-line2" maxLength={200} />
      </div>
      <div className="col-md-5">
        <Field id={id('city')} label="City" value={value.city} onChange={set('city')} error={errors.city} autoComplete="address-level2" maxLength={80} />
      </div>
      <div className="col-md-4">
        <Field id={id('state')} as="select" label="State" value={value.state} onChange={set('state')} error={errors.state} autoComplete="address-level1">
          <option value="">Select state</option>
          {value.state && !INDIAN_STATES.includes(value.state) && <option value={value.state}>{value.state}</option>}
          {INDIAN_STATES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </Field>
      </div>
      <div className="col-md-3">
        <Field id={id('pincode')} label="PIN code" value={value.pincode} onChange={set('pincode')} error={errors.pincode} inputMode="numeric" maxLength={6} autoComplete="postal-code" />
      </div>
      {showLabel && (
        <div className="col-md-6">
          <Field id={id('label')} label="Label (optional)" placeholder="Home, Office…" value={value.label} onChange={set('label')} error={errors.label} maxLength={30} />
        </div>
      )}
    </div>
  )
}

/** One-line summary used on address cards. */
export function formatAddress(a) {
  return [a.addressLine1, a.addressLine2, `${a.city}, ${a.state} ${a.pincode}`].filter(Boolean).join(', ')
}
