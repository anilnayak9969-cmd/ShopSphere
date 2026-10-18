import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useWishlist } from '../context/WishlistContext'
import { initials, labelize } from '../utils/format'

/** "My account" area: a side menu on desktop, a scrollable tab row on mobile. */
export default function AccountLayout() {
  const { user } = useAuth()
  const { count } = useWishlist()

  const links =
    user.role === 'CUSTOMER'
      ? [
          { to: '/account', label: 'Profile', icon: 'bi-person', end: true },
          { to: '/account/orders', label: 'Orders', icon: 'bi-bag-check' },
          { to: '/account/returns', label: 'Returns & refunds', icon: 'bi-arrow-counterclockwise' },
          { to: '/account/payments', label: 'Payments', icon: 'bi-credit-card' },
          { to: '/account/wishlist', label: 'Wishlist', icon: 'bi-heart', count },
          { to: '/account/addresses', label: 'Addresses', icon: 'bi-geo-alt' },
          { to: '/account/reviews', label: 'My reviews', icon: 'bi-star' },
          { to: '/account/notifications', label: 'Notifications', icon: 'bi-bell' },
        ]
      : [
          { to: '/account', label: 'Profile', icon: 'bi-person', end: true },
          { to: '/account/notifications', label: 'Notifications', icon: 'bi-bell' },
        ]

  return (
    <div className="container ss-page">
      <div className="row g-4">
        <aside className="col-lg-3">
          <div className="ss-account-nav">
            <div className="ss-account-user">
              <span className="ss-avatar" style={{ width: 46, height: 46 }}>
                {initials(user.fullName)}
              </span>
              <div className="min-w-0">
                <div className="fw-bold text-truncate">{user.fullName}</div>
                <div className="small text-muted text-truncate">{labelize(user.role)} account</div>
              </div>
            </div>
            <nav aria-label="Account">
              {links.map((l) => (
                <NavLink key={l.to} to={l.to} end={l.end} className="ss-account-link">
                  <i className={`bi ${l.icon}`} aria-hidden="true" />
                  {l.label}
                  {l.count > 0 && <span className="ss-account-count">{l.count}</span>}
                </NavLink>
              ))}
              {user.role === 'SELLER' && (
                <Link to="/seller" className="ss-account-link">
                  <i className="bi bi-shop" aria-hidden="true" /> Seller studio
                </Link>
              )}
              {user.role === 'ADMIN' && (
                <Link to="/admin" className="ss-account-link">
                  <i className="bi bi-speedometer2" aria-hidden="true" /> Admin console
                </Link>
              )}
            </nav>
          </div>
        </aside>
        <div className="col-lg-9">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
