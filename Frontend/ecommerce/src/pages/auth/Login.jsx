import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { homePathFor, useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import authService from '../../services/authService'
import publicService from '../../services/publicService'
import { Field, PasswordInput } from '../../components/FormControls'
import { Alert } from '../../components/ui'
import { getErrorCode, getErrorMessage, getFieldErrors } from '../../utils/errors'
import { EMAIL_RE } from '../../utils/validators'
import { LOGIN_ROLES, ROLE_INFO } from '../../utils/roles'
import AuthShell from './AuthShell'

const ADMIN_ASIDE = {
  asideTitle: 'Run the marketplace.',
  asideText: 'Review sellers and products, monitor orders and payments, and keep ShopSphere running smoothly.',
  image: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=1400&q=80',
  points: [
    { icon: 'bi-key', text: 'New admins register with the secret team access code' },
    { icon: 'bi-speedometer2', text: 'Live dashboards calculated from the database' },
    { icon: 'bi-person-check', text: 'Approve sellers, products and delivery partners' },
  ],
}

/**
 * One sign-in form for every role — the server decides the role from the account. The tabs only
 * tailor the copy; if someone signs in on the "wrong" tab they are simply taken to their own dashboard.
 */
export default function Login() {
  const { login } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()

  const [role, setRole] = useState(LOGIN_ROLES.includes(params.get('role')) ? params.get('role') : 'CUSTOMER')
  const [form, setForm] = useState({ email: location.state?.email || '', password: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [needsVerification, setNeedsVerification] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [demoAccounts, setDemoAccounts] = useState([])

  // Demo logins exist only while the backend runs with demo data (never in production).
  useEffect(() => {
    publicService.getDemoAccounts().then(setDemoAccounts).catch(() => setDemoAccounts([]))
  }, [])

  const info = ROLE_INFO[role]
  const aside = role === 'ADMIN' ? ADMIN_ASIDE : info
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const submit = async (event, credentials = form) => {
    event?.preventDefault()
    const found = {}
    if (!EMAIL_RE.test(credentials.email.trim())) found.email = 'Enter a valid email address'
    if (!credentials.password) found.password = 'Password is required'
    setErrors(found)
    setFormError(null)
    setNeedsVerification(false)
    if (Object.keys(found).length) return

    setSubmitting(true)
    try {
      const user = await login(credentials.email.trim(), credentials.password)
      const first = user.fullName.split(' ')[0]
      if (user.role !== role && credentials === form) {
        toast.success(`Welcome back, ${first}. You are signed in to your ${ROLE_INFO[user.role]?.label.toLowerCase() || ''} account.`)
      } else {
        toast.success(`Welcome back, ${first}`)
      }
      const from = location.state?.from
      // Return to the page they came from only if this role is allowed there (customers use the store;
      // other roles their own area), otherwise go to the role's home.
      const home = homePathFor(user.role)
      const allowed = from?.pathname && (user.role === 'CUSTOMER' || (home !== '/' && from.pathname.startsWith(home)))
      const destination = allowed ? `${from.pathname}${from.search || ''}` : home
      navigate(destination, { replace: true })
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
      setNeedsVerification(getErrorCode(err) === 'EMAIL_NOT_VERIFIED')
    } finally {
      setSubmitting(false)
    }
  }

  const goVerify = async () => {
    try {
      await authService.resendOtp(form.email.trim(), 'REGISTRATION')
    } catch (err) {
      // A cooldown error just means a code was sent moments ago — still go to the verify page.
      if (getErrorCode(err) !== 'TOO_MANY_REQUESTS') toast.error(getErrorMessage(err))
    }
    navigate('/verify-email', { state: { email: form.email.trim() } })
  }

  return (
    <AuthShell
      image={aside.image}
      asideTitle={aside.asideTitle}
      asideText={aside.asideText}
      points={aside.points}
      topLink={
        <>
          {role === 'ADMIN' ? 'New admin?' : 'New to ShopSphere?'} <Link to={`/register?role=${role}`}>{role === 'ADMIN' ? 'Register with access code' : 'Create an account'}</Link>
        </>
      }
    >
      <span className="ss-eyebrow">Sign in</span>
      <h1>Good to see you</h1>

      <div className="ss-login-tabs" role="tablist" aria-label="I am signing in as">
        {LOGIN_ROLES.map((r) => (
          <button
            type="button"
            key={r}
            role="tab"
            aria-selected={r === role}
            className={r === role ? 'active' : ''}
            onClick={() => setRole(r)}
          >
            <i className={`bi ${ROLE_INFO[r].icon}`} aria-hidden="true" />
            {ROLE_INFO[r].label}
          </button>
        ))}
      </div>
      <p className="text-muted mb-4 ss-auth-rise" key={role}>
        {info.loginHint}
      </p>

      {formError && (
        <Alert variant={needsVerification ? 'warning' : 'danger'} className="mb-3">
          {formError}
          {needsVerification && (
            <div className="mt-2">
              <button type="button" className="btn-link-ink" onClick={goVerify}>
                Send me a verification code
              </button>
            </div>
          )}
        </Alert>
      )}

      <form onSubmit={submit} noValidate className="d-grid gap-3">
        <Field id="email" label="Email" type="email" autoComplete="email" value={form.email} onChange={set('email')} error={errors.email} />
        <PasswordInput id="password" label="Password" value={form.password} onChange={set('password')} error={errors.password} />
        <div className="text-end">
          <Link to="/forgot-password" state={{ email: form.email }} className="small">
            Forgot password?
          </Link>
        </div>
        <button type="submit" className="btn btn-ink btn-lg" disabled={submitting}>
          {submitting && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
          Sign in{role !== 'CUSTOMER' && ` as ${info.label.toLowerCase()}`}
        </button>
      </form>

      {demoAccounts.length > 0 && (
        <div className="ss-demo-box mt-4">
          <div className="d-flex align-items-center gap-2 mb-2">
            <i className="bi bi-lightning-charge-fill" aria-hidden="true" />
            <strong className="small">Try a demo account</strong>
            <span className="small text-muted">— one click, no sign-up</span>
          </div>
          <div className="ss-demo-grid">
            {demoAccounts.filter((a, i, all) => all.findIndex((b) => b.role === a.role) === i).map((a) => (
              <button
                type="button"
                key={a.email}
                className="ss-demo-btn"
                disabled={submitting}
                onClick={() => {
                  const credentials = { email: a.email, password: a.password }
                  setRole(a.role)
                  setForm(credentials)
                  submit(null, credentials)
                }}
              >
                <i className={`bi ${ROLE_INFO[a.role]?.icon || 'bi-person'}`} aria-hidden="true" />
                <span>
                  <strong>{ROLE_INFO[a.role]?.label}</strong>
                  <small>{a.email}</small>
                </span>
              </button>
            ))}
          </div>
          {demoAccounts.length > 4 && (
            <div className="mt-2">
              <div className="small text-muted mb-1">Other demo stores (orders go to the store that sells the product):</div>
              <div className="d-flex flex-wrap gap-1">
                {demoAccounts
                  .filter((a, i, all) => all.findIndex((b) => b.role === a.role) !== i)
                  .map((a) => (
                    <button
                      type="button"
                      key={a.email}
                      className="ss-demo-chip"
                      disabled={submitting}
                      title={a.email}
                      onClick={() => {
                        const credentials = { email: a.email, password: a.password }
                        setRole(a.role)
                        setForm(credentials)
                        submit(null, credentials)
                      }}
                    >
                      <i className="bi bi-shop me-1" aria-hidden="true" />
                      {a.label}
                    </button>
                  ))}
              </div>
            </div>
          )}
          <p className="small text-muted mb-0 mt-2">
            {/* Each button signs in with its own password; list them so they can also be typed in. */}
            {[...new Set(demoAccounts.map((a) => a.password))].length === 1 ? 'Demo password: ' : 'Passwords: '}
            {demoAccounts
              .filter((a, i, all) => all.findIndex((b) => b.password === a.password) === i)
              .map((a, i, unique) => (
                <span key={a.password}>
                  {unique.length > 1 && `${ROLE_INFO[a.role]?.label === 'Admin' ? 'admin' : 'demo accounts'} `}
                  <code>{a.password}</code>
                  {i < unique.length - 1 && ' · '}
                </span>
              ))}
          </p>
        </div>
      )}

      {role === 'ADMIN' && (
        <p className="small text-muted text-center mt-4 mb-0">
          New admin? <Link to="/register?role=ADMIN">Register with the admin access code</Link>
        </p>
      )}

      {role !== 'ADMIN' && (
        <p className="small text-muted text-center mt-4 mb-0">
          {role === 'CUSTOMER' && 'Want to sell or deliver instead? '}
          {role === 'CUSTOMER' ? (
            <>
              <Link to="/register?role=SELLER">Become a seller</Link> · <Link to="/register?role=DELIVERY_PARTNER">Deliver with us</Link>
            </>
          ) : (
            <>
              No {info.label.toLowerCase()} account yet? <Link to={`/register?role=${role}`}>Apply now</Link>
            </>
          )}
        </p>
      )}
    </AuthShell>
  )
}
