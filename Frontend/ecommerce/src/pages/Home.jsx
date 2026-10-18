import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import categoryService, { toCategoryTree } from '../services/categoryService'
import productService from '../services/productService'
import publicService from '../services/publicService'
import { useAuth } from '../context/AuthContext'
import CategoryCard from '../components/CategoryCard'
import { CountUp, Reveal, RotatingWord } from '../components/Motion'
import ProductCard from '../components/ProductCard'
import SearchBar from '../components/SearchBar'
import { ProductGridSkeleton, SafeImage } from '../components/ui'
import { formatPrice } from '../utils/format'
import { getRecentlyViewed } from '../utils/recentlyViewed'

/** Real promises the platform keeps (see Settings and the order flow) — scrolled in the trust strip. */
const TRUST_POINTS = [
  { icon: 'bi-patch-check-fill', text: 'Verified sellers' },
  { icon: 'bi-shield-lock-fill', text: 'Razorpay secure payments' },
  { icon: 'bi-cash-coin', text: 'Cash on delivery' },
  { icon: 'bi-truck', text: 'Track every item' },
  { icon: 'bi-arrow-counterclockwise', text: 'Easy returns' },
  { icon: 'bi-star-fill', text: 'Reviews from verified buyers' },
]

const BENEFITS = [
  { icon: 'bi-patch-check', title: 'Verified sellers', text: 'Every store and product is reviewed before it goes live.' },
  { icon: 'bi-shield-lock', title: 'Secure payments', text: 'UPI, cards and net banking via Razorpay, or pay on delivery.' },
  { icon: 'bi-truck', title: 'Fast delivery', text: 'Track every item from packing to your doorstep.' },
  { icon: 'bi-arrow-counterclockwise', title: 'Easy returns', text: 'Request a return from your order page after delivery.' },
]

/** Loads one product row; resolves to [] on error so one failing section never breaks the page. */
function useProducts(params, filter = () => true) {
  const [items, setItems] = useState(null)
  const key = JSON.stringify(params)
  useEffect(() => {
    let cancelled = false
    productService
      .getProducts(JSON.parse(key))
      .then((page) => !cancelled && setItems(page.content.filter(filter)))
      .catch(() => !cancelled && setItems([]))
    return () => {
      cancelled = true
    }
  }, [key])
  return items
}

function ProductRow({ eyebrow, title, link, linkLabel = 'View all', items, cols = 'col-6 col-md-4 col-lg-3' }) {
  if (items && items.length === 0) return null
  return (
    <Reveal as="section" className="ss-section">
      <div className="ss-section-head">
        <div>
          <span className="ss-eyebrow">{eyebrow}</span>
          <h2>{title}</h2>
        </div>
        {link && (
          <Link to={link} className="ss-link-arrow">
            {linkLabel} <i className="bi bi-arrow-right" />
          </Link>
        )}
      </div>
      {items === null ? (
        <ProductGridSkeleton count={4} cols={cols} />
      ) : (
        <div className="row g-3">
          {items.map((p) => (
            <div key={p.productId} className={cols}>
              <ProductCard product={p} />
            </div>
          ))}
        </div>
      )}
    </Reveal>
  )
}

export default function Home() {
  const { user } = useAuth()
  const [departments, setDepartments] = useState([])
  const [settings, setSettings] = useState(null)
  const [coupons, setCoupons] = useState([])
  const [recent] = useState(getRecentlyViewed)
  const [catalog, setCatalog] = useState(null) // { products, brands } — live counts for the hero

  useEffect(() => {
    categoryService
      .getCategories()
      .then((list) => {
        // Show top-level departments with the product count of the whole department.
        const tree = toCategoryTree(list)
        setDepartments(
          tree.map((d) => ({
            ...d,
            total: d.activeProductCount + d.children.reduce((s, c) => s + c.activeProductCount, 0),
          })),
        )
      })
      .catch(() => setDepartments([]))
    publicService.getSettings().then(setSettings).catch(() => setSettings(null))
    publicService.getActiveCoupons().then(setCoupons).catch(() => setCoupons([]))
    Promise.all([productService.getProducts({ size: 1 }), productService.getBrands()])
      .then(([page, brands]) => setCatalog({ products: page.totalElements, brands: brands.length }))
      .catch(() => setCatalog(null))
  }, [])

  const featured = useProducts({ sort: 'rating', minRating: 4, size: 4 })
  const deals = useProducts({ sort: 'discount', minDiscount: 10, inStock: true, size: 8 })
  const bestSellers = useProducts({ sort: 'popular', size: 8 }, (p) => p.soldCount > 0)
  const newArrivals = useProducts({ sort: 'newest', size: 8 })
  const lastViewed = recent[0]
  const recommended = useProducts(
    lastViewed ? { search: lastViewed.brand, size: 8 } : { sort: 'popular', size: 4, page: 1 },
    (p) => !recent.some((r) => r.productId === p.productId),
  )
  const collagePool = useProducts({ sort: 'discount', inStock: true, size: 40 })
  const collage = (collagePool || [])
    .filter((p) => p.imageUrl)
    .filter((p, i, all) => all.findIndex((q) => q.categoryId === p.categoryId) === i)
    .slice(0, 4)
  const coupon = coupons[0]

  return (
    <div className="container ss-page">
      {/* 1. Hero + search */}
      <section className="ss-hero">
        <span className="ss-hero-orb ss-hero-orb-1" aria-hidden="true" />
        <span className="ss-hero-orb ss-hero-orb-2" aria-hidden="true" />
        <div className="row align-items-center g-4">
          <div className="col-lg-7 ss-hero-copy">
            <span className="ss-eyebrow">Welcome to ShopSphere</span>
            <h1>
              Discover products <br className="d-none d-sm-inline" />
              you&apos;ll <RotatingWord words={['love', 'trust', 'need']} />
            </h1>
            <p>
              Thousands of choices from independent, verified sellers — electronics, fashion, home, beauty and more,
              with secure checkout and doorstep delivery.
            </p>
            <div className="my-4">
              <SearchBar id="search-hero" />
            </div>
            <div className="d-flex flex-wrap gap-2">
              <Link to="/products" className="btn btn-gold btn-lg">
                Shop Now
              </Link>
              <a href="#categories" className="btn btn-outline-light-gold btn-lg">
                Explore Categories
              </a>
            </div>
            {/* Live numbers from the catalogue and settings — nothing here is invented. */}
            {(catalog || settings) && (
              <div className="ss-hero-stats">
                {catalog && (
                  <div>
                    <strong>
                      <CountUp value={catalog.products} />
                    </strong>
                    <span>Products live</span>
                  </div>
                )}
                {catalog && catalog.brands > 0 && (
                  <div>
                    <strong>
                      <CountUp value={catalog.brands} />
                    </strong>
                    <span>Brands</span>
                  </div>
                )}
                {departments.length > 0 && (
                  <div>
                    <strong>
                      <CountUp value={departments.length} />
                    </strong>
                    <span>Departments</span>
                  </div>
                )}
                {settings && (
                  <div>
                    <strong>{formatPrice(settings.freeShippingThreshold)}</strong>
                    <span>Free delivery from</span>
                  </div>
                )}
              </div>
            )}
          </div>
          <div className="col-lg-5 d-none d-lg-block">
            <div className="ss-hero-collage ss-hero-float">
              {(collage.length === 4 ? collage : Array.from({ length: 4 }, () => null)).map((p, i) =>
                p ? (
                  <Link key={p.productId} to={`/products/${p.productId}`} aria-label={p.productName}>
                    <SafeImage src={p.imageUrl} alt={p.productName} eager width={500} />
                  </Link>
                ) : (
                  <div key={i} className="ss-skeleton" style={{ opacity: 0.15 }} />
                ),
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Trust strip: scrolls gently; pauses on hover; static with reduced motion */}
      <div className="ss-marquee" aria-label="Why shop with us">
        <div className="ss-marquee-track">
          {[0, 1].map((copy) => (
            <ul key={copy} aria-hidden={copy === 1}>
              {TRUST_POINTS.map((t) => (
                <li key={t.text}>
                  <i className={`bi ${t.icon}`} aria-hidden="true" />
                  {t.text}
                </li>
              ))}
            </ul>
          ))}
        </div>
      </div>

      {/* 2. Categories */}
      {departments.length > 0 && (
        <Reveal as="section" className="ss-section" id="categories">
          <div className="ss-section-head">
            <div>
              <span className="ss-eyebrow">Shop by department</span>
              <h2>Popular categories</h2>
            </div>
            <Link to="/products" className="ss-link-arrow">
              All products <i className="bi bi-arrow-right" />
            </Link>
          </div>
          <div className="row g-3 row-cols-2 row-cols-sm-3 row-cols-md-4 row-cols-lg-6">
            {departments.map((d, i) => (
              <Reveal key={d.categoryId} className="col" delay={i * 70}>
                <CategoryCard category={d} productCount={d.total} />
              </Reveal>
            ))}
          </div>
        </Reveal>
      )}

      {/* 3. Featured (top rated — only shown once genuine ratings exist) */}
      <ProductRow eyebrow="Customer favourites" title="Featured products" link="/products?sort=rating" items={featured} />

      {/* 4. Deals and offers */}
      {coupon && (
        <Reveal as="section" className="ss-section">
          <div className="ss-band ss-band-orange ss-shine">
            <div className="row align-items-center g-3">
              <div className="col-md-8">
                <span className="ss-eyebrow">Today&apos;s offer</span>
                <h2 className="mt-1 mb-1">{coupon.description || `Save with ${coupon.code}`}</h2>
                <p className="mb-0">
                  Use code <span className="ss-coupon-code">{coupon.code}</span> at checkout
                  {Number(coupon.minOrderAmount) > 0 && <> on orders above {formatPrice(coupon.minOrderAmount)}</>}.
                </p>
              </div>
              <div className="col-md-4 text-md-end">
                <Link to="/products?minDiscount=10&sort=discount" className="btn btn-primary-ss btn-lg">
                  Shop deals
                </Link>
              </div>
            </div>
          </div>
        </Reveal>
      )}
      <ProductRow eyebrow="Limited-time prices" title="Deals of the day" link="/products?minDiscount=10&sort=discount" linkLabel="All deals" items={deals} />

      {/* 5. Best sellers (only products that have actually sold) */}
      <ProductRow eyebrow="Most bought" title="Best sellers" link="/products?sort=popular" items={bestSellers} />

      {/* 6. New arrivals */}
      <ProductRow eyebrow="Just in" title="New arrivals" link="/products?sort=newest" items={newArrivals} />

      {/* 7. Recommended (based on what this browser viewed recently) */}
      {recent.length > 0 && (
        <ProductRow eyebrow="Picked for you" title="Recommended for you" items={recommended?.slice(0, 4) ?? null} />
      )}

      {/* 8. Shopping benefits */}
      <Reveal as="section" className="ss-section">
        <div className="ss-promise">
          {BENEFITS.map((b) => (
            <div key={b.title}>
              <i className={`bi ${b.icon}`} aria-hidden="true" />
              <div>
                <h6>{b.title}</h6>
                <p>{b.text}</p>
              </div>
            </div>
          ))}
        </div>
      </Reveal>

      {/* Recently viewed */}
      {recent.length > 0 && (
        <section className="ss-section">
          <div className="ss-section-head">
            <div>
              <span className="ss-eyebrow">Pick up where you left off</span>
              <h2>Recently viewed</h2>
            </div>
          </div>
          <div className="ss-recent">
            {recent.map((p) => (
              <Link key={p.productId} to={`/products/${p.productId}`} className="ss-recent-item">
                <div className="ss-recent-thumb">
                  <SafeImage src={p.imageUrl} alt="" />
                </div>
                <div className="small text-muted">{p.brand}</div>
                <div className="fw-semibold text-truncate">{p.productName}</div>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* 9. Promotional banner */}
      <Reveal as="section" className="ss-section">
        <div className="ss-band ss-shine">
          <div className="row align-items-center g-3">
            <div className="col-lg-8">
              <span className="ss-eyebrow">{user?.role === 'CUSTOMER' ? 'Free delivery' : 'Sell on ShopSphere'}</span>
              <h2 className="mt-1">
                {user?.role === 'CUSTOMER' || !settings
                  ? `Free delivery on orders above ${settings ? formatPrice(settings.freeShippingThreshold) : '₹499'}`
                  : 'Reach thousands of shoppers with your own store'}
              </h2>
              <p className="mb-0">
                {user?.role === 'CUSTOMER'
                  ? 'Add a little more to your cart and shipping is on us.'
                  : 'Register as a seller, get approved and start listing products from your seller dashboard.'}
              </p>
            </div>
            <div className="col-lg-4 text-lg-end">
              {user?.role === 'CUSTOMER' ? (
                <Link to="/cart" className="btn btn-gold btn-lg">
                  View cart
                </Link>
              ) : (
                <Link to={user ? '/products' : '/register?role=SELLER'} className="btn btn-gold btn-lg">
                  {user ? 'Browse products' : 'Become a seller'}
                </Link>
              )}
            </div>
          </div>
        </div>
      </Reveal>
    </div>
  )
}
