import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import authService from '../../services/authService'
import { useToast } from '../../context/ToastContext'
import { Field } from '../../components/FormControls'
import { Alert } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { EMAIL_RE } from '../../utils/validators'
import AuthShell from './AuthShell'

export default function ForgotPassword() {
  const location = useLocation()
  const navigate = useNavigate()
  const toast = useToast()
  const [email, setEmail] = useState(location.state?.email || '')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    if (!EMAIL_RE.test(email.trim())) {
      setError('Enter a valid email address')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const response = await authService.forgotPassword(email.trim())
      toast.info(response.message)
      navigate('/reset-password', { state: { email: email.trim().toLowerCase() } })
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell asideTitle="It happens to the best of us." asideText="We'll email you a one-time code so you can choose a new password.">
      <span className="ss-eyebrow">Forgot password</span>
      <h1>Reset your password</h1>
      <p className="text-muted mb-4">Enter the email you registered with.</p>

      {error && (
        <Alert variant="danger" className="mb-3">
          {error}
        </Alert>
      )}

      <form onSubmit={submit} noValidate className="d-grid gap-3">
        <Field id="email" label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <button type="submit" className="btn btn-ink btn-lg" disabled={submitting}>
          {submitting && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
          Send reset code
        </button>
      </form>
      <p className="small mt-4 mb-0">
        Remembered it? <Link to="/login">Back to sign in</Link>
      </p>
    </AuthShell>
  )
}
