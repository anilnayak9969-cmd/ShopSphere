import { Link } from 'react-router-dom'
import { SafeImage } from '../../components/ui'

const DEFAULT_IMAGE = 'https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=1400&q=80'

const DEFAULT_POINTS = [
  { icon: 'bi-patch-check', text: 'Every seller is reviewed before listing' },
  { icon: 'bi-shield-lock', text: 'Payments secured by Razorpay' },
  { icon: 'bi-envelope-check', text: 'Accounts protected with email verification' },
]

/**
 * Standalone full-screen layout for sign-in / sign-up pages — no store header or footer,
 * so the customer focuses on one task. Left: editorial image panel. Right: the form.
 */
export default function AuthShell({ asideTitle, asideText, image = DEFAULT_IMAGE, points = DEFAULT_POINTS, topLink, wide = false, children }) {
  return (
    <div className="ss-authpage">
      <aside className="ss-authpage-visual">
        {/* Keyed by image so switching roles cross-fades the picture and the copy. */}
        <SafeImage key={image} src={image} alt="" className="ss-auth-fade" />
        <div className="ss-authpage-overlay">
          <Link to="/" className="ss-brand ss-brand-light">
            <span className="ss-brand-mark" aria-hidden="true" />
            ShopSphere
          </Link>
          <div key={asideTitle} className="ss-auth-rise">
            <h2>{asideTitle}</h2>
            {asideText && <p>{asideText}</p>}
            <ul>
              {points.map((p) => (
                <li key={p.text}>
                  <i className={`bi ${p.icon}`} aria-hidden="true" />
                  {p.text}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </aside>

      <main className="ss-authpage-main">
        <header className="ss-authpage-top">
          <Link to="/" className="ss-brand d-lg-none">
            <span className="ss-brand-mark" aria-hidden="true" />
            ShopSphere
          </Link>
          <Link to="/" className="ss-authpage-back d-none d-lg-inline-flex">
            <i className="bi bi-arrow-left" aria-hidden="true" /> Back to store
          </Link>
          {topLink && <div className="small text-muted">{topLink}</div>}
        </header>
        <div className={`ss-authpage-form ${wide ? 'ss-authpage-form-wide' : ''}`}>{children}</div>
        <footer className="ss-authpage-foot">
          <span>© {new Date().getFullYear()} ShopSphere</span>
          <span>
            <i className="bi bi-lock me-1" aria-hidden="true" />
            Secured with encrypted passwords &amp; one-time codes
          </span>
        </footer>
      </main>
    </div>
  )
}
