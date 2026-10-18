import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import orderService from '../services/orderService'
import CheckoutSteps from '../components/CheckoutSteps'
import { PageLoader, SafeImage, StatusBadge } from '../components/ui'
import { formatDateTime, formatPrice } from '../utils/format'

/** Step 4 of checkout. Every state shown here comes from the order and payment saved on the server. */
export default function OrderConfirmation() {
  const { id } = useParams()
  const [order, setOrder] = useState(null)
  const [missing, setMissing] = useState(false)

  useEffect(() => {
    orderService
      .getOrder(id)
      .then(setOrder)
      .catch(() => setMissing(true))
  }, [id])

  if (missing) {
    // Unpaid online checkouts are not shown as orders: the payment did not complete.
    return (
      <div className="container ss-page" style={{ maxWidth: 760 }}>
        <div className="ss-card ss-confirm-hero">
          <div className="ss-confirm-icon failed">
            <i className="bi bi-x-lg" aria-hidden="true" />
          </div>
          <h1 className="h3">Payment not completed</h1>
          <p className="text-muted">No money was taken for this order. Your cart is still saved.</p>
          <Link to="/cart" className="btn btn-ink">
            Return to cart
          </Link>
        </div>
      </div>
    )
  }
  if (!order) return <PageLoader label="Confirming your order…" />

  const cancelled = order.status === 'CANCELLED'
  const cod = order.paymentMethod === 'COD'
  const state = cancelled
    ? { icon: 'bi-x-lg', tone: 'failed', title: 'Order cancelled', text: order.statusNote }
    : cod
      ? { icon: 'bi-truck', tone: 'pending', title: 'Order confirmed', text: `Pay ${formatPrice(order.totalAmount)} in cash when it arrives.` }
      : { icon: 'bi-check-lg', tone: '', title: 'Order confirmed', text: 'Your payment was successful.' }

  return (
    <div className="container ss-page" style={{ maxWidth: 900 }}>
      <CheckoutSteps current={cancelled ? 2 : 4} />
      <div className="ss-card ss-confirm-hero">
        <div className={`ss-confirm-icon ${state.tone}`}>
          <i className={`bi ${state.icon}`} aria-hidden="true" />
        </div>
        <h1 className="h3 mb-1">{state.title}</h1>
        <p className="text-muted mb-2">{state.text}</p>
        <p className="mb-3">
          Order number <strong>{order.orderNumber}</strong> · {formatDateTime(order.createdAt)}
        </p>
        <div className="d-flex justify-content-center gap-2 flex-wrap">
          <StatusBadge status={order.status} />
          <StatusBadge status={order.paymentStatus} label={order.paymentStatus === 'PENDING' ? 'Pay on delivery' : `Payment ${(order.paymentStatus || '').toLowerCase()}`} />
        </div>
      </div>

      <div className="row g-3 mt-1">
        <div className="col-md-7">
          <div className="ss-card ss-card-pad h-100">
            <h2 className="h5">Items</h2>
            {order.items.map((item) => (
              <div key={item.orderItemId} className="d-flex gap-3 align-items-center py-2">
                <div className="ss-line-thumb ss-line-thumb-sm">
                  <SafeImage src={item.imageUrl} alt="" />
                </div>
                <div className="flex-grow-1 min-w-0">
                  <div className="fw-semibold text-truncate">{item.productName}</div>
                  <div className="small text-muted">
                    Qty {item.quantity} · {item.storeName}
                  </div>
                </div>
                <div className="fw-semibold">{formatPrice(item.lineTotal)}</div>
              </div>
            ))}
            <div className="ss-divider" />
            <div className="ss-summary-row ss-summary-total">
              <span>Total</span>
              <span>{formatPrice(order.totalAmount)}</span>
            </div>
          </div>
        </div>
        <div className="col-md-5">
          <div className="ss-card ss-card-pad h-100">
            <h2 className="h5">Delivery address</h2>
            <p className="small text-muted">
              {order.shippingName}
              <br />
              {order.addressLine1}
              {order.addressLine2 && `, ${order.addressLine2}`}
              <br />
              {order.city}, {order.state} {order.pincode}
              <br />
              {order.shippingPhone}
            </p>
            <h2 className="h5 mt-3">What happens next</h2>
            <p className="small text-muted mb-3">
              Each seller confirms, packs and ships their items. You&apos;ll get a notification at every step, and tracking
              numbers appear on your order page.
            </p>
            <Link to={`/account/orders/${order.orderId}`} className="btn btn-ink w-100">
              Track this order
            </Link>
            <Link to="/products" className="btn btn-ghost w-100 mt-2">
              Continue shopping
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
