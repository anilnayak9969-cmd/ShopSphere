import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import accountService from '../services/accountService'
import cartService from '../services/cartService'
import orderService from '../services/orderService'
import paymentService from '../services/paymentService'
import publicService from '../services/publicService'
import AddressForm, { EMPTY_ADDRESS, formatAddress, validateAddress } from '../components/AddressForm'
import CheckoutSteps from '../components/CheckoutSteps'
import CouponBox from '../components/CouponBox'
import { Alert, PageLoader, SafeImage } from '../components/ui'
import { getErrorMessage, getFieldErrors } from '../utils/errors'
import { formatPrice } from '../utils/format'
import { loadRazorpay } from '../utils/razorpay'
import { getSavedCoupon, saveCoupon } from '../utils/couponStorage'

const NEW_ADDRESS = 'new'

/**
 * Step 1 Address → Step 2 Review → Step 3 Payment → Step 4 Confirmation (separate page).
 * The server prices the order; a Razorpay success in the browser is only accepted after the
 * server verifies its signature.
 */
export default function Checkout() {
  const { user } = useAuth()
  const { cart, loading, refresh } = useCart()
  const navigate = useNavigate()

  const [step, setStep] = useState(0)
  const [addresses, setAddresses] = useState(null)
  const [settings, setSettings] = useState(null)
  const [selected, setSelected] = useState(NEW_ADDRESS)
  const [form, setForm] = useState({ ...EMPTY_ADDRESS, fullName: user?.fullName || '', phone: user?.phone || '' })
  const [saveAddress, setSaveAddress] = useState(true)
  const [errors, setErrors] = useState({})
  const [quote, setQuote] = useState(null)
  const [method, setMethod] = useState('RAZORPAY')
  const [stage, setStage] = useState('idle') // idle | creating | paying | verifying
  const [paymentError, setPaymentError] = useState(null)

  useEffect(() => {
    accountService
      .getAddresses()
      .then((list) => {
        setAddresses(list)
        const preferred = list.find((a) => a.defaultAddress) || list[0]
        if (preferred) setSelected(preferred.addressId)
      })
      .catch(() => setAddresses([]))
    publicService.getSettings().then(setSettings).catch(() => setSettings(null))
  }, [])

  // Carry over a coupon chosen in the cart — re-validated by the server for this cart.
  const subtotal = cart?.subtotal
  useEffect(() => {
    const saved = getSavedCoupon()
    if (!saved || subtotal === undefined) return
    cartService
      .applyCoupon(saved)
      .then(setQuote)
      .catch(() => {
        setQuote(null)
        saveCoupon('')
      })
  }, [subtotal])

  if (loading && !cart) return <PageLoader />
  if (cart && cart.items.length === 0 && stage === 'idle') return <Navigate to="/cart" replace />
  if (cart && cart.hasPriceChanges && stage === 'idle') return <Navigate to="/cart" replace />
  if (!cart || addresses === null) return <PageLoader />

  const busy = stage !== 'idle'
  const totals = quote
    ? { discount: quote.discount, shipping: quote.shippingFee, total: quote.total }
    : { discount: 0, shipping: cart.shippingFee, total: cart.total }
  const address = selected === NEW_ADDRESS ? form : addresses.find((a) => a.addressId === selected)
  const codAllowed = settings?.codEnabled && Number(totals.total) <= Number(settings.codMaxAmount)

  const continueFromAddress = (event) => {
    event.preventDefault()
    if (selected === NEW_ADDRESS) {
      const found = validateAddress(form)
      setErrors(found)
      if (Object.keys(found).length) return
      if (saveAddress) {
        accountService
          .createAddress({ ...form, defaultAddress: addresses.length === 0 })
          .then((saved) => {
            setAddresses((list) => [...list, saved])
            setSelected(saved.addressId)
          })
          .catch(() => {}) // saving is a convenience; checkout continues either way
      }
    }
    setStep(1)
  }

  const goToOrder = (orderId, extra = {}) =>
    navigate(`/order-confirmation/${orderId}`, { replace: true, state: { justPlaced: true, ...extra } })

  const verify = async (response, orderId) => {
    setStage('verifying')
    try {
      await paymentService.verifyPayment({
        razorpayOrderId: response.razorpay_order_id,
        razorpayPaymentId: response.razorpay_payment_id,
        razorpaySignature: response.razorpay_signature,
      })
      saveCoupon('')
      await refresh()
      goToOrder(orderId)
    } catch (err) {
      setPaymentError(getErrorMessage(err))
      setStage('idle')
    }
  }

  const placeOrder = async () => {
    if (busy) return // prevents duplicate submissions
    setPaymentError(null)
    setStage('creating')

    if (method === 'RAZORPAY' && !(await loadRazorpay())) {
      setPaymentError('Could not load Razorpay. Check your internet connection and try again.')
      setStage('idle')
      return
    }

    let checkout
    try {
      checkout = await orderService.checkout({
        shippingName: address.fullName,
        shippingPhone: address.phone,
        addressLine1: address.addressLine1,
        addressLine2: address.addressLine2 || null,
        city: address.city,
        state: address.state,
        pincode: address.pincode,
        couponCode: quote?.code || null,
        paymentMethod: method,
      })
    } catch (err) {
      const fieldErrors = getFieldErrors(err)
      if (Object.keys(fieldErrors).length) {
        setErrors({ fullName: fieldErrors.shippingName, phone: fieldErrors.shippingPhone, ...fieldErrors })
        setStep(0)
      }
      setPaymentError(getErrorMessage(err))
      if (err.response?.data?.code?.startsWith('COUPON')) {
        setQuote(null)
        saveCoupon('')
      }
      setStage('idle')
      refresh()
      return
    }

    if (checkout.paymentMethod === 'COD') {
      saveCoupon('')
      await refresh()
      goToOrder(checkout.orderId)
      return
    }

    setStage('paying')
    const razorpay = new window.Razorpay({
      key: checkout.keyId,
      amount: checkout.amount,
      currency: checkout.currency,
      order_id: checkout.razorpayOrderId,
      name: 'ShopSphere',
      description: `Order ${checkout.orderNumber}`,
      prefill: { name: checkout.customerName, email: checkout.customerEmail, contact: checkout.customerPhone },
      notes: { orderNumber: checkout.orderNumber },
      theme: { color: '#2874F0' },
      handler: (response) => verify(response, checkout.orderId),
      modal: {
        ondismiss: () => {
          paymentService.paymentFailed(checkout.razorpayOrderId, 'Customer closed the payment window').catch(() => {})
          setStage('idle')
          setPaymentError('Payment was not completed. Your cart is saved — you can try again.')
        },
      },
    })
    razorpay.on('payment.failed', (response) => {
      paymentService.paymentFailed(checkout.razorpayOrderId, response.error?.description).catch(() => {})
      setPaymentError(response.error?.description || 'The payment failed. Please try again.')
    })
    razorpay.open()
  }

  const buttonLabel = {
    idle: method === 'COD' ? `Place order · Pay ${formatPrice(totals.total)} on delivery` : `Pay ${formatPrice(totals.total)} securely`,
    creating: 'Placing your order…',
    paying: 'Waiting for payment…',
    verifying: 'Confirming your payment…',
  }[stage]

  const summary = (
    <div className="ss-card ss-card-pad" style={{ position: 'sticky', top: 130 }}>
      <h2 className="h5 mb-3">Price details</h2>
      <div className="ss-summary-row">
        <span>Items ({cart.itemCount})</span>
        <span>{formatPrice(cart.subtotal)}</span>
      </div>
      {Number(totals.discount) > 0 && (
        <div className="ss-summary-row text-success fw-semibold">
          <span>Coupon {quote.code}</span>
          <span>−{formatPrice(totals.discount)}</span>
        </div>
      )}
      <div className="ss-summary-row">
        <span>Delivery</span>
        <span>{Number(totals.shipping) === 0 ? <span className="text-success">Free</span> : formatPrice(totals.shipping)}</span>
      </div>
      <div className="ss-summary-row text-muted small">
        <span>Taxes</span>
        <span>Included in prices</span>
      </div>
      <div className="ss-divider" />
      <div className="ss-summary-row ss-summary-total">
        <span>Total payable</span>
        <span>{formatPrice(totals.total)}</span>
      </div>
      {Number(totals.discount) > 0 && <div className="small text-success mt-1">You save {formatPrice(totals.discount)} on this order.</div>}
    </div>
  )

  return (
    <div className="container ss-page">
      <Link to="/cart" className="small text-muted text-decoration-none">
        <i className="bi bi-arrow-left me-1" /> Back to cart
      </Link>
      <h1 className="h3 mt-2 mb-3">Checkout</h1>
      <CheckoutSteps current={step} />

      <div className="row g-3">
        <div className="col-lg-8">
          {/* Step 1 — address */}
          {step === 0 && (
            <form className="ss-card ss-card-pad" onSubmit={continueFromAddress} noValidate>
              <h2 className="h5 mb-1">Delivery address</h2>
              <p className="text-muted small mb-3">The address is copied onto your order, so later edits never change it.</p>
              {addresses.length > 0 && (
                <div className="d-grid gap-2 mb-3" role="radiogroup" aria-label="Saved addresses">
                  {addresses.map((a) => (
                    <label key={a.addressId} className={`ss-address-option ${selected === a.addressId ? 'selected' : ''}`}>
                      <input type="radio" name="address" className="form-check-input mt-1" checked={selected === a.addressId} onChange={() => setSelected(a.addressId)} />
                      <span>
                        <strong>{a.fullName}</strong>
                        {a.label && <span className="ss-pill ss-pill-muted ms-2">{a.label}</span>}
                        {a.defaultAddress && <span className="ss-pill ss-pill-info ms-2">Default</span>}
                        <span className="d-block small text-muted mt-1">{formatAddress(a)}</span>
                        <span className="d-block small text-muted">Phone: {a.phone}</span>
                      </span>
                    </label>
                  ))}
                  <label className={`ss-address-option ${selected === NEW_ADDRESS ? 'selected' : ''}`}>
                    <input type="radio" name="address" className="form-check-input mt-1" checked={selected === NEW_ADDRESS} onChange={() => setSelected(NEW_ADDRESS)} />
                    <strong>
                      <i className="bi bi-plus-lg me-1" />
                      Deliver to a new address
                    </strong>
                  </label>
                </div>
              )}
              {selected === NEW_ADDRESS && (
                <>
                  <AddressForm value={form} onChange={setForm} errors={errors} showLabel={false} idPrefix="checkout" />
                  <div className="form-check mt-3">
                    <input id="save-address" type="checkbox" className="form-check-input" checked={saveAddress} onChange={(e) => setSaveAddress(e.target.checked)} />
                    <label htmlFor="save-address" className="form-check-label">
                      Save this address to my address book
                    </label>
                  </div>
                </>
              )}
              <button type="submit" className="btn btn-ink btn-lg mt-4">
                Deliver here
              </button>
            </form>
          )}

          {/* Step 2 — review */}
          {step === 1 && (
            <div className="ss-card ss-card-pad">
              <div className="d-flex justify-content-between align-items-start gap-2 mb-3">
                <div>
                  <h2 className="h5 mb-1">Delivering to {address.fullName}</h2>
                  <div className="small text-muted">{formatAddress(address)} · {address.phone}</div>
                </div>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setStep(0)}>
                  Change
                </button>
              </div>
              <div className="ss-divider" />
              <h2 className="h5">Review items</h2>
              {cart.items.map((item) => (
                <div key={item.cartItemId} className="d-flex gap-3 align-items-center py-2">
                  <div className="ss-line-thumb ss-line-thumb-sm">
                    <SafeImage src={item.imageUrl} alt="" />
                  </div>
                  <div className="flex-grow-1 min-w-0">
                    <div className="fw-semibold text-truncate">{item.productName}</div>
                    <div className="small text-muted">
                      Qty {item.quantity} · Sold by {item.storeName}
                    </div>
                  </div>
                  <div className="fw-semibold">{formatPrice(item.lineTotal)}</div>
                </div>
              ))}
              <div className="ss-divider" />
              <CouponBox quote={quote} onApplied={(q) => { setQuote(q); saveCoupon(q.code) }} onRemoved={() => { setQuote(null); saveCoupon('') }} id="checkout-coupon" />
              <div className="d-flex gap-2 mt-4">
                <button type="button" className="btn btn-ghost" onClick={() => setStep(0)}>
                  Back
                </button>
                <button type="button" className="btn btn-ink btn-lg" onClick={() => setStep(2)} disabled={!cart.checkoutAllowed}>
                  Continue to payment
                </button>
              </div>
            </div>
          )}

          {/* Step 3 — payment */}
          {step === 2 && (
            <div className="ss-card ss-card-pad">
              <h2 className="h5 mb-3">Payment method</h2>
              <div className="d-grid gap-2" role="radiogroup" aria-label="Payment method">
                <label className={`ss-pay-option ${method === 'RAZORPAY' ? 'selected' : ''}`}>
                  <input type="radio" name="method" className="form-check-input mt-1" checked={method === 'RAZORPAY'} onChange={() => setMethod('RAZORPAY')} disabled={busy} />
                  <span>
                    <strong>Pay online</strong>
                    <span className="d-block small text-muted">
                      UPI, cards, net banking or wallets in Razorpay&apos;s secure window. Test card: 4111 1111 1111 1111.
                    </span>
                  </span>
                </label>
                <label className={`ss-pay-option ${method === 'COD' ? 'selected' : ''} ${codAllowed ? '' : 'disabled'}`}>
                  <input type="radio" name="method" className="form-check-input mt-1" checked={method === 'COD'} onChange={() => setMethod('COD')} disabled={busy || !codAllowed} />
                  <span>
                    <strong>Cash on Delivery</strong>
                    <span className="d-block small text-muted">
                      {!settings
                        ? 'Checking availability…'
                        : !settings.codEnabled
                          ? 'Not available at the moment.'
                          : codAllowed
                            ? 'Pay in cash when your order arrives.'
                            : `Available for orders up to ${formatPrice(settings.codMaxAmount)}.`}
                    </span>
                  </span>
                </label>
              </div>
              {paymentError && (
                <Alert variant="danger" className="mt-3">
                  {paymentError}
                </Alert>
              )}
              <div className="d-flex gap-2 mt-4 flex-wrap">
                <button type="button" className="btn btn-ghost" onClick={() => setStep(1)} disabled={busy}>
                  Back
                </button>
                <button type="button" className="btn btn-gold btn-lg flex-grow-1" onClick={placeOrder} disabled={busy || !cart.checkoutAllowed}>
                  {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                  {buttonLabel}
                </button>
              </div>
              <p className="small text-muted mt-3 mb-0">
                <i className="bi bi-shield-lock me-1" />
                ShopSphere never sees or stores your card details. Your order total is calculated by our server.
              </p>
            </div>
          )}
        </div>
        <div className="col-lg-4">{summary}</div>
      </div>
    </div>
  )
}
