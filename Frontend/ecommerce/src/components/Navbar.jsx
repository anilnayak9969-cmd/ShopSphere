import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useWishlist } from '../context/WishlistContext'
import categoryService, { toCategoryTree } from '../services/categoryService'
import { initials, labelize } from '../utils/format'
import NotificationBell from './NotificationBell'
import SearchBar from './SearchBar'

/** Second-row links for each role. Every link opens a real, protected page. */
const ROLE_LINKS = {
  SELLER: [
    { to: '/seller', label: 'Seller Dashboard', end: true },
    { to: '/seller/products', label: 'Products', end: true },
    { to: '/seller/inventory', label: 'Inventory' },
    { to: '/seller/orders', label: 'Orders' },
    { to: '/seller/sales', label: 'Sales' },
    { to: '/seller/profile', label: 'Store Profile' },
  ],
  DELIVERY_PARTNER: [{ to: '/delivery', label: 'Delivery Dashboard', end: true }],
  ADMIN: [
    { to: '/admin', label: 'Admin Dashboard', end: true },
    { to: '/admin/users', label: 'Users' },
    { to: '/admin/sellers', label: 'Sellers' },
    { to: '/admin/categories', label: 'Categories' },
    { to: '/admin/products', label: 'Products' },
    { to: '/admin/orders', label: 'Orders' },
    { to: '/admin/reports', label: 'Reports' },
  ],
}

function AccountMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    const onClick = (e) => !ref.current?.contains(e.target) && setOpen(false)
    const onKey = (e) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [])

  // Close the menu after navigating.
  const [lastPath, setLastPath] = useState(location.pathname)
  if (lastPath !== location.pathname) {
    setLastPath(location.pathname)
    setOpen(false)
  }

  const handleLogout = () => {
    logout()
    navigate('/')
  }

  return (
    <div className="ss-menu-wrap" ref={ref}>
      <button type="button" className="ss-icon-btn" onClick={() => setOpen((v) => !v)} aria-expanded={open} aria-haspopup="menu">
        <span className="ss-avatar" style={{ width: 24, height: 24, fontSize: '0.62rem' }}>
          {initials(user.fullName)}
        </span>
        <span>Account</span>
      </button>
      {open && (
        <div className="ss-menu" role="menu">
          <div className="ss-menu-head">
            <div className="fw-bold text-truncate">{user.fullName}</div>
            <div className="small text-muted text-truncate">{user.email}</div>
            <span className="ss-pill ss-pill-info mt-2">{labelize(user.role)}</span>
          </div>
          <Link to="/account" role="menuitem">
            <i className="bi bi-person-circle" /> My profile
          </Link>
          {user.role === 'CUSTOMER' && (
            <>
              <Link to="/account/orders" role="menuitem">
                <i className="bi bi-bag-check" /> My orders
              </Link>
              <Link to="/account/returns" role="menuitem">
                <i className="bi bi-arrow-counterclockwise" /> Returns &amp; refunds
              </Link>
              <Link to="/account/addresses" role="menuitem">
                <i className="bi bi-geo-alt" /> Addresses
              </Link>
            </>
          )}
          {user.role === 'SELLER' && (
            <Link to="/seller" role="menuitem">
              <i className="bi bi-shop" /> Seller dashboard
            </Link>
          )}
          {user.role === 'ADMIN' && (
            <Link to="/admin" role="menuitem">
              <i className="bi bi-speedometer2" /> Admin dashboard
            </Link>
          )}
          <button type="button" role="menuitem" onClick={handleLogout}>
            <i className="bi bi-box-arrow-right" /> Logout
          </button>
        </div>
      )}
    </div>
  )
}

export default function Navbar() {
  const { user } = useAuth()
  const { count } = useCart()
  const { count: wishlistCount } = useWishlist()
  const location = useLocation()
  const [params] = useSearchParams()
  const [departments, setDepartments] = useState([])

  const isShopper = !user || user.role === 'CUSTOMER'

  useEffect(() => {
    if (!isShopper) return
    categoryService
      .getCategories()
      .then((list) => setDepartments(toCategoryTree(list).filter((d) => d.totalLiveProductCount > 0).slice(0, 7)))
      .catch(() => setDepartments([]))
  }, [isShopper])

  const activeCategory = location.pathname === '/products' ? params.get('categoryId') : null
  const onDeals = location.pathname === '/products' && params.get('minDiscount')
  const roleLinks = user ? ROLE_LINKS[user.role] : null

  return (
    <>
      <div className="ss-topbar">
        <strong>Free delivery</strong> on eligible orders · Secure payments · Easy returns
      </div>
      <header className="ss-header">
        <div className="container">
          <div className="ss-header-inner">
            <Link to="/" className="ss-brand" aria-label="ShopSphere home">
              <span className="ss-brand-mark" aria-hidden="true" />
              Shop<span className="ss-brand-accent">Sphere</span>
            </Link>
            <SearchBar className="d-none d-lg-block" id="search-desktop" />
            <nav className="ss-nav-actions" aria-label="Account and cart">
              {!user && (
                <>
                  <Link to="/login" state={{ from: location }} className="ss-icon-btn">
                    <i className="bi bi-person" aria-hidden="true" />
                    <span>Login</span>
                  </Link>
                  <Link to="/register" className="ss-icon-btn d-none d-sm-inline-flex">
                    <i className="bi bi-person-plus" aria-hidden="true" />
                    <span>Register</span>
                  </Link>
                </>
              )}
              {user?.role === 'CUSTOMER' && (
                <>
                  <NavLink to="/account/orders" className="ss-icon-btn d-none d-md-inline-flex">
                    <i className="bi bi-bag-check" aria-hidden="true" />
                    <span>My Orders</span>
                  </NavLink>
                  <NavLink to="/account/wishlist" className="ss-icon-btn" aria-label={`Wishlist, ${wishlistCount} items`}>
                    <i className="bi bi-heart" aria-hidden="true" />
                    <span>Wishlist</span>
                    {wishlistCount > 0 && <span className="ss-badge">{wishlistCount}</span>}
                  </NavLink>
                </>
              )}
              {user && <NotificationBell />}
              {isShopper && (
                <NavLink to="/cart" className="ss-icon-btn" aria-label={`Cart, ${count} items`}>
                  <i className="bi bi-cart3" aria-hidden="true" />
                  <span>Cart</span>
                  {count > 0 && <span className="ss-badge">{count > 99 ? '99+' : count}</span>}
                </NavLink>
              )}
              {user && <AccountMenu />}
            </nav>
          </div>
          <div className="d-lg-none pb-2">
            <SearchBar id="search-mobile" />
          </div>
        </div>
        <div className="ss-catbar">
          <div className="container">
            <nav className="ss-catbar-inner" aria-label={roleLinks ? 'Workspace' : 'Shop'}>
              {roleLinks ? (
                <>
                  {roleLinks.map((l) => (
                    <NavLink key={l.to} to={l.to} end={l.end}>
                      {l.label}
                    </NavLink>
                  ))}
                  <NavLink to="/" end className="ms-auto">
                    <i className="bi bi-shop-window me-1" aria-hidden="true" />
                    Storefront
                  </NavLink>
                </>
              ) : (
                <>
                  <NavLink to="/" end>
                    Home
                  </NavLink>
                  <Link to="/products" className={location.pathname === '/products' && !activeCategory && !onDeals ? 'active' : ''}>
                    All Products
                  </Link>
                  <Link to="/products?minDiscount=10&sort=discount" className={`ss-catbar-deal ${onDeals ? 'active' : ''}`}>
                    <i className="bi bi-lightning-charge-fill me-1" aria-hidden="true" />
                    Deals
                  </Link>
                  {departments.map((c) => (
                    <Link
                      key={c.categoryId}
                      to={`/products?categoryId=${c.categoryId}`}
                      className={activeCategory === String(c.categoryId) ? 'active' : ''}
                    >
                      {c.categoryName}
                    </Link>
                  ))}
                  <NavLink to="/help">Help</NavLink>
                </>
              )}
            </nav>
          </div>
        </div>
      </header>
    </>
  )
}
