import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCart } from '../context/CartContext'
import { useToast } from '../context/ToastContext'
import { useWishlist } from '../context/WishlistContext'
import cartService from '../services/cartService'
import CouponBox from '../components/CouponBox'
import { Alert, EmptyState, ErrorState, PageLoader, QuantityStepper, SafeImage } from '../components/ui'
import { getErrorMessage } from '../utils/errors'
import { formatPrice } from '../utils/format'
import { getSavedCoupon, saveCoupon } from '../utils/couponStorage'

export default function Cart() {
  const { cart, loading, error, refresh, updateItem, removeItem, saveForLater, acknowledgePrices } = useCart()
  const { refresh: refreshWishlist } = useWishlist()
  const toast = useToast()
  const navigate = useNavigate()
  const [busyItem, setBusyItem] = useState(null)
  const [quote, setQuote] = useState(null)
  const [reviewing, setReviewing] = useState(false)

  // Re-check a coupon chosen earlier against the current cart (quantities may have changed).
  const subtotal = cart?.subtotal
  useEffect(() => {
    const saved = getSavedCoupon()
    if (!saved || subtotal === undefined || Number(subtotal) === 0) {
      setQuote(null)
      return
    }
    cartService
      .applyCoupon(saved)
      .then(setQuote)
      .catch(() => {
        setQuote(null)
        saveCoupon('')
      })
  }, [subtotal])

  if (error && !cart) {
    return (
      <div className="container ss-page">
        <ErrorState message={getErrorMessage(error)} onRetry={refresh} />
      </div>
    )
  }
  if (loading && !cart) return <PageLoader label="Loading your cart…" />

  if (!cart || cart.items.length === 0) {
    return (
      <div className="container ss-page">
        <div className="ss-card">
          <EmptyState icon="bi-cart3" title="Your cart is empty" action={<Link to="/products" className="btn btn-ink">Continue shopping</Link>}>
            Items you add are saved to your account, so they will be here next time too.
          </EmptyState>
        </div>
      </div>
    )
  }

  const run = async (item, action, successMessage) => {
    if (busyItem) return // prevents repeated clicks
    setBusyItem(item.cartItemId)
    try {
      await action()
      if (successMessage) toast.info(successMessage)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusyItem(null)
    }
  }

  const moveToWishlist = (item) =>
    run(item, async () => {
      await saveForLater(item.cartItemId)
      await refreshWishlist()
    }, `${item.productName} saved for later in your wishlist`)

  const reviewPrices = async () => {
    setReviewing(true)
    try {
      await acknowledgePrices()
      toast.success('Prices updated. Please check your totals before checkout.')
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setReviewing(false)
    }
  }

  const onCouponApplied = (q) => {
    setQuote(q)
    saveCoupon(q.code)
    toast.success(`${q.code} applied`)
  }
  const onCouponRemoved = () => {
    setQuote(null)
    saveCoupon('')
  }

  const totals = quote
    ? { discount: quote.discount, shipping: quote.shippingFee, total: quote.total }
    : { discount: 0, shipping: cart.shippingFee, total: cart.total }
  const remainingForFree = Number(cart.freeShippingThreshold) - (Number(cart.subtotal) - Number(totals.discount))
  const hasUnavailable = cart.items.some((i) => !i.available)
  const canCheckout = cart.checkoutAllowed && !cart.hasPriceChanges

  return (
    <div className="container ss-page">
      <div className="d-flex justify-content-between align-items-end flex-wrap gap-2 mb-3">
        <h1 className="h3 mb-0">
          Shopping cart <span className="text-muted fs-6">({cart.itemCount} items)</span>
        </h1>
        <Link to="/products" className="ss-link-arrow">
          <i className="bi bi-arrow-left" /> Continue shopping
        </Link>
      </div>

      {cart.hasPriceChanges && (
        <Alert variant="warning" className="mb-3">
          <strong>Some prices have changed</strong> since you added these items. Please review the highlighted
          prices before checkout.
          <div className="mt-2">
            <button type="button" className="btn btn-sm btn-gold" onClick={reviewPrices} disabled={reviewing}>
              I&apos;ve reviewed the new prices
            </button>
          </div>
        </Alert>
      )}

      <div className="row g-3">
        <div className="col-lg-8">
          <div className="ss-card px-3 px-md-4">
            {cart.items.map((item) => (
              <div key={item.cartItemId} className={`ss-line-item ${item.available ? '' : 'unavailable'}`}>
                <Link to={`/products/${item.productId}`} className="ss-line-thumb">
                  <SafeImage src={item.imageUrl} alt={item.productName} />
                </Link>
                <div className="min-w-0">
                  <Link to={`/products/${item.productId}`} className="ss-product-name" style={{ minHeight: 0 }}>
                    {item.productName}
                  </Link>
                  <div className="small text-muted mb-1">Sold by {item.storeName}</div>
                  <div className="ss-price-row mb-1">
                    <span className="ss-price">{formatPrice(item.unitPrice)}</span>
                    {item.discountPercent > 0 && (
                      <>
                        <span className="ss-mrp">{formatPrice(item.price)}</span>
                        <span className="ss-off">{item.discountPercent}% off</span>
                      </>
                    )}
                  </div>
                  {item.priceChanged && (
                    <div className="ss-price-notice mb-1">
                      <i className="bi bi-arrow-left-right me-1" />
                      Price changed from {formatPrice(item.previousPrice)}
                    </div>
                  )}
                  {item.availabilityMessage && (
                    <div className="small fw-semibold text-danger mb-1">
                      <i className="bi bi-exclamation-circle me-1" />
                      {item.availabilityMessage}
                    </div>
                  )}
                  <div className="d-flex align-items-center gap-3 flex-wrap mt-2">
                    <QuantityStepper
                      value={item.quantity}
                      max={Math.max(1, Math.min(10, item.stockQuantity))}
                      onChange={(q) => run(item, () => updateItem(item.cartItemId, q))}
                      disabled={busyItem === item.cartItemId || item.stockQuantity === 0}
                      label={`Quantity of ${item.productName}`}
                    />
                    <button type="button" className="btn-link-ink small" onClick={() => moveToWishlist(item)} disabled={busyItem === item.cartItemId}>
                      Save for later
                    </button>
                    <button
                      type="button"
                      className="btn-link-ink small text-danger"
                      onClick={() => run(item, () => removeItem(item.cartItemId), `${item.productName} removed from your cart`)}
                      disabled={busyItem === item.cartItemId}
                    >
                      Remove
                    </button>
                  </div>
                </div>
                <div className="text-end fw-bold">{formatPrice(item.lineTotal)}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="col-lg-4">
          <div className="ss-card ss-card-pad" style={{ position: 'sticky', top: 130 }}>
            <h2 className="h5 mb-3">Order summary</h2>
            <CouponBox quote={quote} onApplied={onCouponApplied} onRemoved={onCouponRemoved} id="cart-coupon" />
            <div className="ss-divider" />
            <div className="ss-summary-row">
              <span>Items ({cart.itemCount})</span>
              <span>{formatPrice(cart.subtotal)}</span>
            </div>
            {Number(totals.discount) > 0 && (
              <div className="ss-summary-row text-success fw-semibold">
                <span>Coupon discount</span>
                <span>−{formatPrice(totals.discount)}</span>
              </div>
            )}
            <div className="ss-summary-row">
              <span>Delivery</span>
              <span>{Number(totals.shipping) === 0 ? <span className="text-success">Free</span> : formatPrice(totals.shipping)}</span>
            </div>
            {remainingForFree > 0 && Number(cart.subtotal) > 0 && (
              <div className="small text-muted">Add {formatPrice(remainingForFree)} more for free delivery.</div>
            )}
            <div className="ss-divider" />
            <div className="ss-summary-row ss-summary-total">
              <span>Total</span>
              <span>{formatPrice(totals.total)}</span>
            </div>
            <div className="small text-muted">Prices include all taxes.</div>
            <button type="button" className="btn btn-gold btn-lg w-100 mt-3" disabled={!canCheckout} onClick={() => navigate('/checkout')}>
              Proceed to checkout
            </button>
            {hasUnavailable && <p className="small text-danger mt-2 mb-0">Update or remove the highlighted items to continue.</p>}
            {cart.hasPriceChanges && !hasUnavailable && <p className="small text-warning mt-2 mb-0">Review the price changes above to continue.</p>}
            <p className="small text-muted mt-3 mb-0">
              <i className="bi bi-shield-lock me-1" />
              Prices and discounts are confirmed again by our server at checkout.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
