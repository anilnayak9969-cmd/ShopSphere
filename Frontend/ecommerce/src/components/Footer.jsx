import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const YEAR = new Date().getFullYear()

export default function Footer() {
  const { user } = useAuth()

  return (
    <footer className="ss-footer">
      <div className="container">
        <div className="row g-4">
          <div className="col-lg-4">
            <Link to="/" className="ss-brand ss-brand-light">
              <span className="ss-brand-mark" aria-hidden="true" />
              Shop<span className="ss-brand-accent">Sphere</span>
            </Link>
            <p className="mt-3" style={{ maxWidth: 340 }}>
              A marketplace that brings categories and independent sellers together in one place. Every seller and
              product is reviewed before it goes live.
            </p>
          </div>
          <div className="col-6 col-md-3 col-lg-2">
            <h6>Shop</h6>
            <ul>
              <li>
                <Link to="/products">All products</Link>
              </li>
              <li>
                <Link to="/products?minDiscount=10&sort=discount">Deals</Link>
              </li>
              <li>
                <Link to="/products?sort=newest">New arrivals</Link>
              </li>
              <li>
                <Link to="/products?sort=popular">Best sellers</Link>
              </li>
            </ul>
          </div>
          <div className="col-6 col-md-3 col-lg-2">
            <h6>Customer support</h6>
            <ul>
              <li>
                <Link to="/contact">Contact us</Link>
              </li>
              <li>
                <Link to="/help">FAQs</Link>
              </li>
              <li>
                <Link to="/policies/shipping">Shipping information</Link>
              </li>
              <li>
                <Link to="/policies/returns">Returns &amp; refunds</Link>
              </li>
              {user?.role === 'CUSTOMER' && (
                <li>
                  <Link to="/account/orders">Track my order</Link>
                </li>
              )}
            </ul>
          </div>
          <div className="col-6 col-md-3 col-lg-2">
            <h6>Company</h6>
            <ul>
              <li>
                <Link to="/about">About us</Link>
              </li>
              <li>
                <Link to="/policies/privacy">Privacy policy</Link>
              </li>
              <li>
                <Link to="/policies/terms">Terms &amp; conditions</Link>
              </li>
            </ul>
          </div>
          <div className="col-6 col-md-3 col-lg-2">
            <h6>Sell</h6>
            <ul>
              {!user && (
                <li>
                  <Link to="/register?role=SELLER">Become a seller</Link>
                </li>
              )}
              {user?.role === 'SELLER' && (
                <li>
                  <Link to="/seller">Seller dashboard</Link>
                </li>
              )}
              <li>
                <Link to="/help#account">Selling on ShopSphere</Link>
              </li>
            </ul>
          </div>
        </div>
        <div className="ss-footer-bottom">
          <span>© {YEAR} ShopSphere. Internship project — not a real store.</span>
          <span>
            <i className="bi bi-shield-lock me-1" aria-hidden="true" />
            Payments by Razorpay · Prices include all taxes
          </span>
        </div>
      </div>
    </footer>
  )
}
