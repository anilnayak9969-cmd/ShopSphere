import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { homePathFor, useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import authService from '../../services/authService'
import { OtpInput } from '../../components/FormControls'
import { Alert } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import AuthShell from './AuthShell'

const RESEND_SECONDS = 60

export default function VerifyEmail() {
  const location = useLocation()
  const navigate = useNavigate()
  const { completeLogin } = useAuth()
  const toast = useToast()
  const email = location.state?.email

  const [otp, setOtp] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [cooldown, setCooldown] = useState(RESEND_SECONDS)

  useEffect(() => {
    if (cooldown <= 0) return undefined
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  if (!email) return <Navigate to="/register" replace />

  const submit = async (event) => {
    event.preventDefault()
    if (otp.length !== 6) {
      setError('Enter the 6-digit code from your email.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const user = completeLogin(await authService.verifyOtp(email, otp))
      toast.success(
        user.role === 'SELLER'
          ? 'Email verified. Your store is now awaiting admin approval.'
          : `Welcome to ShopSphere, ${user.fullName.split(' ')[0]}!`,
      )
      navigate(homePathFor(user.role), { replace: true })
    } catch (err) {
      setError(getErrorMessage(err))
      setOtp('')
    } finally {
      setSubmitting(false)
    }
  }

  const resend = async () => {
    setError(null)
    try {
      await authService.resendOtp(email, 'REGISTRATION')
      toast.success('A new code is on its way.')
      setCooldown(RESEND_SECONDS)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <AuthShell asideTitle="One last step." asideText="Verifying your email keeps your account and orders secure.">
      <span className="ss-eyebrow">Verify email</span>
      <h1>Check your inbox</h1>
      <p className="text-muted mb-4">
        We sent a 6-digit code to <strong className="text-body">{email}</strong>. It expires in 10 minutes.
      </p>

      {error && (
        <Alert variant="danger" className="mb-3">
          {error}
        </Alert>
      )}

      <form onSubmit={submit} noValidate>
        <OtpInput value={otp} onChange={setOtp} invalid={Boolean(error)} disabled={submitting} />
        <button type="submit" className="btn btn-ink btn-lg w-100 mt-4" disabled={submitting || otp.length !== 6}>
          {submitting && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
          Verify and continue
        </button>
      </form>

      <div className="d-flex justify-content-between flex-wrap gap-2 mt-4 small">
        {cooldown > 0 ? (
          <span className="text-muted">Resend code in {cooldown}s</span>
        ) : (
          <button type="button" className="btn-link-ink" onClick={resend}>
            Resend code
          </button>
        )}
        <Link to="/register">Use a different email</Link>
      </div>
      <p className="small text-muted mt-4 mb-0">Can&apos;t find it? Check your spam or promotions folder.</p>
    </AuthShell>
  )
}
