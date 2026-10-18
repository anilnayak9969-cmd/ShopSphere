import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import productService from '../services/productService'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useToast } from '../context/ToastContext'
import ProductCard from '../components/ProductCard'
import ProductReviews from '../components/ProductReviews'
import WishlistButton from '../components/WishlistButton'
import { Stars } from '../components/Rating'
import { rememberProduct } from '../utils/recentlyViewed'
import { Alert, EmptyState, PageLoader, PriceTag, QuantityStepper, SafeImage, StockNote } from '../components/ui'
import { getErrorMessage } from '../utils/errors'
import { formatPrice } from '../utils/format'

export default function ProductDetails() {
  const { id } = useParams()
  const { user } = useAuth()
  const { addItem } = useCart()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()

  const [product, setProduct] = useState(null)
  const [related, setRelated] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | missing | error
  const [error, setError] = useState(null)
  const [quantity, setQuantity] = useState(1)
  const [busy, setBusy] = useState(false)

  const [activeImage, setActiveImage] = useState(0)

  const load = useCallback(() => {
    setStatus('loading')
    setQuantity(1)
    setActiveImage(0)
    productService
      .getProduct(id)
      .then((data) => {
        setProduct(data)
        setStatus('ready')
        rememberProduct(data)
        return productService.getProducts({ categoryId: data.categoryId, size: 5 })
      })
      .then((page) => page && setRelated(page.content.filter((p) => String(p.productId) !== String(id)).slice(0, 4)))
      .catch((err) => {
        if (err.response?.status === 404) {
          setError(getErrorMessage(err))
          setStatus('missing')
        }
        else {
          setError(getErrorMessage(err))
          setStatus('error')
        }
      })
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  if (status === 'loading') return <PageLoader label="Loading product…" />
  if (status === 'missing' || status === 'error') {
    return (
      <div className="container ss-page">
        <EmptyState
          icon={status === 'missing' ? 'bi-box' : 'bi-wifi-off'}
          title={status === 'missing' ? 'This product is not available' : 'Could not load this product'}
          action={
            status === 'missing' ? (
              <Link to="/products" className="btn btn-ink">
                Continue shopping
              </Link>
            ) : (
              <button type="button" className="btn btn-ink" onClick={load}>
                Try again
              </button>
            )
          }
        >
          {status === 'missing'
            ? error || 'It may have been removed by the seller or is temporarily unlisted.'
            : error}
        </EmptyState>
      </div>
    )
  }

  const canBuy = !user || user.role === 'CUSTOMER'
  const maxQty = Math.min(10, product.stockQuantity)
  const images = [product.imageUrl, ...(product.galleryImages || [])].filter(Boolean)

  // After a review is added or removed, refresh the stars shown at the top of the page.
  const refreshRating = () =>
    productService
      .getProduct(id)
      .then((fresh) => setProduct((p) => ({ ...p, ratingAverage: fresh.ratingAverage, ratingCount: fresh.ratingCount })))
      .catch(() => {})

  const addToCart = async (goToCart) => {
    if (!user) {
      navigate('/login', { state: { from: location } })
      return
    }
    setBusy(true)
    try {
      await addItem(product.productId, quantity)
      if (goToCart) navigate('/cart')
      else toast.success(`${quantity} × ${product.productName} added to your cart`)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container ss-page">
      <nav aria-label="Breadcrumb" className="small mb-4">
        <Link to="/" className="text-muted text-decoration-none">
          Home
        </Link>
        <span className="mx-2 text-muted">/</span>
        <Link to={`/products?categoryId=${product.categoryId}`} className="text-muted text-decoration-none">
          {product.categoryName}
        </Link>
        <span className="mx-2 text-muted">/</span>
        <span>{product.productName}</span>
      </nav>

      <div className="row g-4 g-lg-5">
        <div className="col-lg-6">
          <div className="ss-pd-gallery">
            <div className="ss-pd-media">
              <SafeImage key={images[activeImage]} src={images[activeImage]} alt={product.productName} />
            </div>
            {images.length > 1 && (
              <div className="ss-pd-thumbs" role="tablist" aria-label="Product photos">
                {images.map((src, i) => (
                  <button
                    type="button"
                    key={src}
                    role="tab"
                    aria-selected={i === activeImage}
                    aria-label={`Photo ${i + 1}`}
                    className={i === activeImage ? 'active' : ''}
                    onClick={() => setActiveImage(i)}
                  >
                    <SafeImage src={src} alt="" />
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
        <div className="col-lg-6">
          <span className="ss-eyebrow">{product.brand || product.categoryName}</span>
          <h1 className="ss-pd-title">{product.productName}</h1>
          {product.ratingCount > 0 && (
            <a href="#reviews" className="text-decoration-none d-inline-block mb-2">
              <Stars value={product.ratingAverage} count={product.ratingCount} size="md" />
            </a>
          )}
          <p className="text-muted mb-4">
            Sold by <strong className="text-body">{product.storeName}</strong>
          </p>

          <PriceTag
            price={product.price}
            sellingPrice={product.sellingPrice}
            discountPercent={product.discountPercent}
            large
          />
          <p className="small text-muted mt-1 mb-3">Inclusive of all taxes</p>
          <StockNote stock={product.stockQuantity} />

          <div className="ss-divider" />

          {canBuy ? (
            product.inStock ? (
              <div className="d-flex flex-wrap align-items-center gap-3">
                <QuantityStepper value={quantity} max={maxQty} onChange={setQuantity} large disabled={busy} />
                <button type="button" className="btn btn-ink btn-lg flex-grow-1" onClick={() => addToCart(false)} disabled={busy}>
                  <i className="bi bi-bag-plus me-2" />
                  Add to cart
                </button>
                <button type="button" className="btn btn-gold btn-lg flex-grow-1" onClick={() => addToCart(true)} disabled={busy}>
                  Buy now
                </button>
                <WishlistButton product={product} variant="button" />
              </div>
            ) : (
              <>
                <Alert variant="warning">This piece is currently out of stock. Save it to your wishlist and check back soon.</Alert>
                <div className="mt-3">
                  <WishlistButton product={product} variant="button" />
                </div>
              </>
            )
          ) : (
            <Alert variant="info">You are signed in as a {user.role.toLowerCase()}. Use a customer account to buy.</Alert>
          )}

          <div className="ss-card ss-card-pad mt-4">
            <div className="d-flex gap-3">
              <i className="bi bi-truck fs-4 text-muted" aria-hidden="true" />
              <div className="small">
                <strong>Delivery</strong>
                <div className="text-muted">
                  {Number(product.sellingPrice) >= 499
                    ? 'Free shipping on this item.'
                    : `Free shipping on orders above ${formatPrice(499)}; otherwise ${formatPrice(49)}.`}
                </div>
              </div>
            </div>
            <div className="ss-divider" />
            <div className="d-flex gap-3">
              <i className="bi bi-shield-check fs-4 text-muted" aria-hidden="true" />
              <div className="small">
                <strong>Secure payment</strong>
                <div className="text-muted">UPI, cards and net banking through Razorpay.</div>
              </div>
            </div>
          </div>

          <h2 className="h3 mt-5">Description</h2>
          <p style={{ whiteSpace: 'pre-line' }}>{product.description}</p>

          <h2 className="h3 mt-4">Specifications</h2>
          <table className="ss-spec">
            <tbody>
              <tr>
                <th scope="row">Brand</th>
                <td>{product.brand || '—'}</td>
              </tr>
              <tr>
                <th scope="row">Category</th>
                <td>{product.categoryName}</td>
              </tr>
              <tr>
                <th scope="row">SKU</th>
                <td>{product.sku}</td>
              </tr>
              <tr>
                <th scope="row">Seller</th>
                <td>{product.storeName}</td>
              </tr>
              {product.specifications?.map((s) => (
                <tr key={s.name}>
                  <th scope="row">{s.name}</th>
                  <td>{s.value}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <ProductReviews productId={product.productId} onChanged={refreshRating} />

      {related.length > 0 && (
        <section className="ss-section">
          <div className="ss-section-head">
            <div>
              <span className="ss-eyebrow">You may also like</span>
              <h2>More in {product.categoryName}</h2>
            </div>
          </div>
          <div className="row g-3 g-md-4">
            {related.map((p) => (
              <div key={p.productId} className="col-6 col-md-3">
                <ProductCard product={p} />
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
