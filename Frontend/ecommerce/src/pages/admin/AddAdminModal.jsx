import { useState } from 'react'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import { Field, PasswordInput } from '../../components/FormControls'
import { Alert } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { EMAIL_RE, PASSWORD_MESSAGE, PASSWORD_RE, PERSON_NAME_RE, PHONE_RE } from '../../utils/validators'

const EMPTY = { fullName: '', email: '', phone: '', password: '' }

/**
 * An existing admin adds another admin here and shares the first password, which the new admin should change
 * from My account. (New admins can also sign up themselves with the admin access code.)
 */
export default function AddAdminModal({ open, onClose, onCreated }) {
  const toast = useToast()
  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [busy, setBusy] = useState(false)

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))
  const close = () => {
    if (busy) return
    setForm(EMPTY)
    setErrors({})
    setFormError(null)
    onClose()
  }

  const submit = async (event) => {
    event.preventDefault()
    const found = {}
    const name = form.fullName.trim()
    if (name.length < 2) found.fullName = 'Enter the full name'
    else if (!PERSON_NAME_RE.test(name)) found.fullName = "Use letters, spaces and . ' - only"
    if (!EMAIL_RE.test(form.email.trim())) found.email = 'Enter a valid email address'
    if (!PHONE_RE.test(form.phone)) found.phone = 'Enter a valid 10-digit mobile number'
    if (!PASSWORD_RE.test(form.password)) found.password = PASSWORD_MESSAGE
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return
    setBusy(true)
    try {
      const created = await adminService.createAdmin({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone,
        password: form.password,
      })
      toast.success(`${created.fullName} can now sign in as an admin.`)
      setForm(EMPTY)
      onCreated(created)
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open={open}
      title="Add an admin"
      subtitle="The new admin can sign in straight away with this password."
      onClose={close}
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={close} disabled={busy}>
            Cancel
          </button>
          <button type="submit" form="add-admin-form" className="btn btn-ink" disabled={busy}>
            {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
            Create admin
          </button>
        </>
      }
    >
      <form id="add-admin-form" onSubmit={submit} noValidate className="d-grid gap-3">
        {formError && <Alert variant="danger">{formError}</Alert>}
        <Field id="admin-name" label="Full name" value={form.fullName} onChange={set('fullName')} error={errors.fullName} maxLength={100} />
        <div className="row g-3">
          <div className="col-md-7">
            <Field id="admin-email" type="email" label="Work email" value={form.email} onChange={set('email')} error={errors.email} />
          </div>
          <div className="col-md-5">
            <Field
              id="admin-phone"
              label="Mobile number"
              inputMode="numeric"
              maxLength={10}
              value={form.phone}
              onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value.replace(/\D/g, '') }))}
              error={errors.phone}
            />
          </div>
        </div>
        <PasswordInput id="admin-password" label="First password" autoComplete="new-password" value={form.password} onChange={set('password')} error={errors.password} showRules />
        <Alert variant="info">Admins have full access to users, orders, payments and settings. Share the password privately and ask them to change it after signing in.</Alert>
      </form>
    </Modal>
  )
}
