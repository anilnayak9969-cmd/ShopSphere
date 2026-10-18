import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import authService from '../../services/authService'
import { useToast } from '../../context/ToastContext'
import { Field, OtpInput, PasswordInput } from '../../components/FormControls'
import { Alert } from '../../components/ui'
import { getErrorCode, getErrorMessage, getFieldErrors } from '../../utils/errors'
import { EMAIL_RE, PASSWORD_MESSAGE, PASSWORD_RE } from '../../utils/validators'
import AuthShell from './AuthShell'

export default function ResetPassword() {
  const location = useLocation()
  const navigate = useNavigate()
  const toast = useToast()
  const [form, setForm] = useState({ email: location.state?.email || '', otp: '', newPassword: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const submit = async (event) => {
    event.preventDefault()
    const found = {}
    if (!EMAIL_RE.test(form.email.trim())) found.email = 'Enter a valid email address'
    if (form.otp.length !== 6) found.otp = 'Enter the 6-digit code'
    if (!PASSWORD_RE.test(form.newPassword)) found.newPassword = PASSWORD_MESSAGE
    if (form.confirm !== form.newPassword) found.confirm = 'Passwords do not match'
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return

    setSubmitting(true)
    try {
      const response = await authService.resetPassword({
        email: form.email.trim(),
        otp: form.otp,
        newPassword: form.newPassword,
      })
      toast.success(response.message)
      navigate('/login', { replace: true, state: { email: form.email.trim() } })
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
      if (getErrorCode(err)?.startsWith('OTP')) setForm((f) => ({ ...f, otp: '' }))
    } finally {
      setSubmitting(false)
    }
  }

  const resend = async () => {
    try {
      await authService.forgotPassword(form.email.trim())
      toast.info('If the email is registered, a new code has been sent.')
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  return (
    <AuthShell asideTitle="Choose a fresh password." asideText="Use the code we emailed you. It works once and expires in 10 minutes.">
      <span className="ss-eyebrow">Reset password</span>
      <h1>Set a new password</h1>
      <p className="text-muted mb-4">
        Didn&apos;t get a code?{' '}
        <button type="button" className="btn-link-ink" onClick={resend} disabled={!EMAIL_RE.test(form.email.trim())}>
          Send another
        </button>
      </p>

      {formError && (
        <Alert variant="danger" className="mb-3">
          {formError}
        </Alert>
      )}

      <form onSubmit={submit} noValidate className="d-grid gap-3">
        <Field id="email" label="Email" type="email" autoComplete="email" value={form.email} onChange={set('email')} error={errors.email} />
        <div>
          <span className="form-label d-block">Verification code</span>
          <OtpInput value={form.otp} onChange={(otp) => setForm((f) => ({ ...f, otp }))} invalid={Boolean(errors.otp)} />
          {errors.otp && <div className="invalid-feedback d-block">{errors.otp}</div>}
        </div>
        <PasswordInput id="newPassword" label="New password" autoComplete="new-password" value={form.newPassword} onChange={set('newPassword')} error={errors.newPassword} showRules />
        <PasswordInput id="confirm" label="Confirm new password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} error={errors.confirm} />
        <button type="submit" className="btn btn-ink btn-lg mt-2" disabled={submitting}>
          {submitting && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
          Update password
        </button>
      </form>
      <p className="small mt-4 mb-0">
        <Link to="/login">Back to sign in</Link>
      </p>
    </AuthShell>
  )
}
