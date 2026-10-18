import { Suspense, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import NotificationBell from '../components/NotificationBell'
import { PageLoader } from '../components/ui'
import { initials, labelize } from '../utils/format'

/** Sidebar layout shared by the seller studio and the admin console. */
export default function DashboardLayout({ sectionLabel, links }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [open, setOpen] = useState(false)

  // Close the mobile sidebar whenever the page changes.
  const [lastPath, setLastPath] = useState(location.pathname)
  if (lastPath !== location.pathname) {
    setLastPath(location.pathname)
    setOpen(false)
  }

  const current = links.find((l) => (l.end ? location.pathname === l.to : location.pathname.startsWith(l.to)))

  return (
    <div className="ss-dash">
      <aside className={`ss-sidebar ${open ? 'open' : ''}`} aria-label={`${sectionLabel} navigation`}>
        <Link to="/" className="ss-brand ss-brand-light">
          <span className="ss-brand-mark" aria-hidden="true" />
          ShopSphere
        </Link>
        <div className="ss-side-label">{sectionLabel}</div>
        <nav>
          {links.map((link) => (
            <NavLink key={link.to} to={link.to} end={link.end} className="ss-side-link">
              <i className={`bi ${link.icon}`} aria-hidden="true" />
              {link.label}
              {link.count > 0 && <span className="ss-side-count">{link.count}</span>}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto pt-4">
          <Link to="/" className="ss-side-link">
            <i className="bi bi-arrow-up-right-square" aria-hidden="true" />
            Visit store
          </Link>
          <button
            type="button"
            className="ss-side-link w-100"
            onClick={() => {
              logout()
              navigate('/')
            }}
          >
            <i className="bi bi-box-arrow-right" aria-hidden="true" />
            Sign out
          </button>
        </div>
      </aside>
      {open && <div className="ss-sidebar-backdrop" onClick={() => setOpen(false)} aria-hidden="true" />}

      <div className="ss-dash-main">
        <header className="ss-dash-topbar">
          <button
            type="button"
            className="btn btn-ghost btn-sm d-lg-none"
            onClick={() => setOpen(true)}
            aria-label="Open navigation"
          >
            <i className="bi bi-list" />
          </button>
          <div className="fw-bold text-truncate">{current?.label || sectionLabel}</div>
          <div className="ms-auto d-flex align-items-center gap-2">
            <NotificationBell />
            <div className="text-end d-none d-sm-block lh-sm">
              <div className="fw-semibold small">{user?.fullName}</div>
              <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                {labelize(user?.role)}
              </div>
            </div>
            <span className="ss-avatar">{initials(user?.fullName)}</span>
          </div>
        </header>
        <main className="ss-dash-content">
          {/* Pages load on demand; keep the sidebar visible while they do. */}
          <Suspense fallback={<PageLoader />}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  )
}
