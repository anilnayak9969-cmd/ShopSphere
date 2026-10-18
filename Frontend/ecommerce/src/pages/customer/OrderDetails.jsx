import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import orderService from '../../services/orderService'
import { useToast } from '../../context/ToastContext'
import { useLiveEvent, useLiveStatus } from '../../context/LiveContext'
import Modal, { ConfirmDialog } from '../../components/Modal'
import { Alert, EmptyState, PageLoader, SafeImage, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate, formatDateTime, formatPrice, labelize } from '../../utils/format'

const STEPS = ['PLACED', 'CONFIRMED', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED']
const STEP_ICONS = ['bi-receipt', 'bi-check2', 'bi-box-seam', 'bi-truck', 'bi-geo-alt', 'bi-house-check']
export const RETURN_REASONS = [
  ['DAMAGED', 'Item arrived damaged'],
  ['WRONG_ITEM', 'Received the wrong item'],
  ['NOT_AS_DESCRIBED', 'Not as described'],
  ['SIZE_OR_FIT', 'Size or fit issue'],
  ['NO_LONGER_NEEDED', 'No longer needed'],
  ['OTHER', 'Other'],
]

/** Tracking history for one item, newest first. Falls back to known timestamps for older orders. */
function TrackingHistory({ item, placedAt }) {
  const events = item.tracking?.length
    ? item.tracking.map((e) => ({ key: `${e.status}-${e.at}`, text: e.description, at: e.at, status: e.status }))
    : [
        item.shippedAt && { key: 'shipped', text: `Shipped via ${item.courierName}`, at: item.shippedAt, status: 'SHIPPED' },
        item.deliveredAt && { key: 'delivered', text: 'Delivered', at: item.deliveredAt, status: 'DELIVERED' },
      ].filter(Boolean)
  const rows = [...events].reverse().concat({ key: 'placed', text: 'Order placed', at: placedAt, status: 'PLACED' })
  return (
    <ol className="ss-track-list" aria-label={`Tracking history for ${item.productName}`}>
      {rows.map((r, i) => (
        <li key={r.key} className={i === 0 ? 'latest' : ''}>
          <span className="ss-track-text">{r.text}</span>
          <span className="ss-track-time">{formatDateTime(r.at)}</span>
        </li>
      ))}
    </ol>
  )
}

function Timeline({ status }) {
  const current = STEPS.indexOf(status)
  if (current === -1) return null
  return (
    <div className="ss-timeline" aria-label={`Order status: ${labelize(status)}`}>
      {STEPS.map((step, i) => (
        <div key={step} className={`ss-timeline-step ${i <= current ? 'done' : ''} ${i === current ? 'current' : ''}`}>
          <div className="ss-timeline-dot">
            <i className={`bi ${STEP_ICONS[i]}`} aria-hidden="true" />
          </div>
          <span>{labelize(step)}</span>
        </div>
      ))}
    </div>
  )
}

/** "Who has it now" for one item, in plain words (null once the journey is over). */
function nowWith(item) {
  const store = item.storeName
  switch (item.itemStatus) {
    case 'PLACED':
      return { icon: 'bi-shop', text: `With the seller — waiting for ${store} to confirm your order` }
    case 'CONFIRMED':
      return { icon: 'bi-box-seam', text: `With the seller — ${store} is packing it` }
    case 'PACKED':
      return { icon: 'bi-hourglass-split', text: `Packed by ${store} — waiting for a delivery partner to pick it up` }
    case 'SHIPPED':
      return item.deliveryPartnerName
        ? { icon: 'bi-bicycle', text: `With delivery partner ${item.deliveryPartnerName} — picked up from ${store}` }
        : { icon: 'bi-truck', text: `On the way with ${item.courierName || 'the courier'}` }
    case 'OUT_FOR_DELIVERY':
      return { icon: 'bi-geo-alt', text: item.deliveryPartnerName ? `Out for delivery with ${item.deliveryPartnerName} — arriving today` : 'Out for delivery — arriving today' }
    default:
      return null
  }
}

export default function OrderDetails() {
  const { id } = useParams()
  const toast = useToast()
  const [order, setOrder] = useState(null)
  const [error, setError] = useState(null)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [busy, setBusy] = useState(false)
  const [returnItem, setReturnItem] = useState(null)
  const [returnForm, setReturnForm] = useState({ reason: '', comments: '' })
  const [returnError, setReturnError] = useState(null)
  const [showHistory, setShowHistory] = useState({})
  const live = useLiveStatus()

  // Quiet refresh (no loader) used by live updates and the fallback poll.
  const refresh = useCallback(() => {
    orderService.getOrder(id).then(setOrder).catch(() => {})
  }, [id])

  // Live tracking: the seller or delivery partner changed something on this order.
  useLiveEvent((type, data) => {
    if (type === 'notification' && data?.link === `/account/orders/${id}`) refresh()
  })

  const inProgress = Boolean(order?.items.some((i) => ['PLACED', 'CONFIRMED', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY'].includes(i.itemStatus)))
  // If the live connection is down, check every 20 seconds while the order is on its way.
  useEffect(() => {
    if (live || !inProgress) return undefined
    const timer = setInterval(refresh, 20000)
    return () => clearInterval(timer)
  }, [live, inProgress, refresh])

  const load = useCallback(() => {
    setError(null)
    orderService
      .getOrder(id)
      .then(setOrder)
      .catch((err) => setError(err))
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  if (error) {
    return (
      <div className="container ss-page">
        <EmptyState
          icon="bi-receipt"
          title={error.response?.status === 404 ? 'Order not found' : 'Could not load this order'}
          action={<Link to="/account/orders" className="btn btn-ink">Back to my orders</Link>}
        >
          {error.response?.status === 404 ? 'This order does not exist on your account.' : getErrorMessage(error)}
        </EmptyState>
      </div>
    )
  }
  if (!order) return <PageLoader label="Loading order…" />

  const activeItems = order.items.filter((i) => !['CANCELLED', 'RETURNED', 'REFUNDED'].includes(i.itemStatus))
  const canCancel = activeItems.length > 0 && activeItems.every((i) => ['PLACED', 'CONFIRMED'].includes(i.itemStatus))

  const cancel = async () => {
    setBusy(true)
    try {
      setOrder(await orderService.cancelOrder(order.orderId))
      toast.success('Your order has been cancelled.')
      setConfirmCancel(false)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const openReturn = (item) => {
    setReturnItem(item)
    setReturnForm({ reason: '', comments: '' })
    setReturnError(null)
  }

  const submitReturn = async (event) => {
    event.preventDefault()
    if (!returnForm.reason) {
      setReturnError('Choose a reason')
      return
    }
    setBusy(true)
    try {
      await orderService.requestReturn(returnItem.orderItemId, returnForm.reason, returnForm.comments.trim() || null)
      toast.success('Return requested. The seller will review it shortly.')
      setReturnItem(null)
      load()
    } catch (err) {
      setReturnError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container ss-page" style={{ maxWidth: 1080 }}>
      <Link to="/account/orders" className="small text-muted text-decoration-none">
        <i className="bi bi-arrow-left me-1" /> All orders
      </Link>

      {order.statusNote && (
        <Alert variant={order.status === 'CANCELLED' ? 'warning' : 'info'} className="mt-3">
          {order.statusNote}
        </Alert>
      )}

      <div className="d-flex justify-content-between align-items-end flex-wrap gap-3 mt-3 mb-3">
        <div>
          <span className="ss-eyebrow">Order</span>
          <h1 className="h3 m-0">{order.orderNumber}</h1>
          <div className="text-muted small">Placed {formatDateTime(order.createdAt)}</div>
        </div>
        <div className="d-flex gap-2 align-items-center flex-wrap">
          {inProgress && (
            <span className={`ss-live-pill ${live ? 'on' : ''}`} title={live ? 'Updates appear here instantly' : 'Checking for updates every 20 seconds'}>
              <span className="ss-live-dot" aria-hidden="true" />
              {live ? 'Live tracking' : 'Auto-refresh'}
            </span>
          )}
          <span key={order.status} className="ss-status-pop">
            <StatusBadge status={order.status} />
          </span>
          {canCancel && (
            <button type="button" className="btn btn-danger-soft btn-sm" onClick={() => setConfirmCancel(true)}>
              Cancel order
            </button>
          )}
        </div>
      </div>

      {STEPS.includes(order.status) && (
        <div className="ss-card ss-card-pad mb-3">
          <Timeline key={order.status} status={order.status} />
          <p className="small text-muted text-center mb-0 mt-2">
            Shows the least advanced item — items from different sellers may move at different speeds.
          </p>
        </div>
      )}

      <div className="row g-3">
        <div className="col-lg-8">
          <div className="ss-card px-3 px-md-4">
            {order.items.map((item) => (
              <div key={item.orderItemId} className="ss-line-item">
                <Link to={`/products/${item.productId}`} className="ss-line-thumb">
                  <SafeImage src={item.imageUrl} alt={item.productName} />
                </Link>
                <div className="min-w-0">
                  <Link to={`/products/${item.productId}`} className="ss-product-name" style={{ minHeight: 0 }}>
                    {item.productName}
                  </Link>
                  <div className="small text-muted">
                    Sold by {item.storeName} · {item.quantity} × {formatPrice(item.unitPrice)}
                  </div>
                  <div className="ss-item-status">
                    <StatusBadge status={item.itemStatus} />
                    {item.trackingNumber && (
                      <span className="ss-tracking">
                        <i className="bi bi-truck me-1" />
                        {item.courierName} · {item.trackingNumber}
                        {item.deliveryPartnerName && ` · ${item.deliveryPartnerName} is delivering`}
                      </span>
                    )}
                    {item.deliveredAt && <span className="text-muted">Delivered {formatDate(item.deliveredAt)}</span>}
                    {item.returnStatus && <StatusBadge status={item.returnStatus} label={`Return ${labelize(item.returnStatus).toLowerCase()}`} />}
                  </div>
                  {nowWith(item) && (
                    <div className="small fw-semibold mt-1 ss-now-with" key={item.itemStatus} style={{ color: 'var(--ss-blue)' }}>
                      <i className={`bi ${nowWith(item).icon} me-1`} aria-hidden="true" />
                      {nowWith(item).text}
                    </div>
                  )}
                  <button
                    type="button"
                    className="btn btn-link btn-sm p-0 mt-1 small text-decoration-none"
                    aria-expanded={Boolean(showHistory[item.orderItemId])}
                    onClick={() => setShowHistory((s) => ({ ...s, [item.orderItemId]: !s[item.orderItemId] }))}
                  >
                    <i className={`bi ${showHistory[item.orderItemId] ? 'bi-chevron-up' : 'bi-clock-history'} me-1`} aria-hidden="true" />
                    {showHistory[item.orderItemId] ? 'Hide tracking history' : 'Tracking history'}
                  </button>
                  {showHistory[item.orderItemId] && <TrackingHistory item={item} placedAt={order.createdAt} />}
                  {item.returnable && (
                    <button type="button" className="btn btn-ghost btn-sm mt-2" onClick={() => openReturn(item)}>
                      <i className="bi bi-arrow-counterclockwise me-1" />
                      Return item
                    </button>
                  )}
                  {item.returnable && item.returnableUntil && (
                    <div className="small text-muted mt-1">Return window closes {formatDate(item.returnableUntil)}</div>
                  )}
                  {item.itemStatus === 'DELIVERED' && (
                    <Link to={`/products/${item.productId}#reviews`} className="small d-inline-block mt-1 ms-2">
                      Write a review
                    </Link>
                  )}
                </div>
                <div className="fw-bold text-end">{formatPrice(item.lineTotal)}</div>
              </div>
            ))}
          </div>
        </div>
        <div className="col-lg-4">
          <div className="ss-card ss-card-pad mb-3">
            <h2 className="h6">Delivering to</h2>
            <div className="fw-semibold">{order.shippingName}</div>
            <div className="small text-muted">
              {order.addressLine1}
              {order.addressLine2 && <>, {order.addressLine2}</>}
              <br />
              {order.city}, {order.state} {order.pincode}
              <br />
              {order.shippingPhone}
            </div>
          </div>
          <div className="ss-card ss-card-pad">
            <h2 className="h6">Payment</h2>
            <div className="ss-summary-row">
              <span>Subtotal</span>
              <span>{formatPrice(order.subtotal)}</span>
            </div>
            {Number(order.discountAmount) > 0 && (
              <div className="ss-summary-row text-success fw-semibold">
                <span>Coupon {order.couponCode}</span>
                <span>−{formatPrice(order.discountAmount)}</span>
              </div>
            )}
            <div className="ss-summary-row">
              <span>Delivery</span>
              <span>{Number(order.shippingFee) === 0 ? 'Free' : formatPrice(order.shippingFee)}</span>
            </div>
            <div className="ss-divider" />
            <div className="ss-summary-row ss-summary-total">
              <span>Total</span>
              <span>{formatPrice(order.totalAmount)}</span>
            </div>
            <div className="d-flex justify-content-between align-items-center mt-3 small">
              <span className="text-muted">{order.paymentMethod === 'COD' ? 'Cash on Delivery' : 'Paid online (Razorpay)'}</span>
              <StatusBadge status={order.paymentStatus} label={order.paymentStatus === 'PENDING' ? 'Pay on delivery' : undefined} />
            </div>
            {Number(order.refundedAmount) > 0 && (
              <div className="ss-summary-row small text-success mt-2">
                <span>Refunded</span>
                <span>{formatPrice(order.refundedAmount)}</span>
              </div>
            )}
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={confirmCancel}
        title="Cancel this order?"
        confirmLabel="Yes, cancel order"
        danger
        busy={busy}
        onConfirm={cancel}
        onCancel={() => setConfirmCancel(false)}
      >
        All items go back into stock.{' '}
        {order.paymentMethod === 'COD'
          ? 'Nothing will be charged for this Cash on Delivery order.'
          : `${formatPrice(order.totalAmount)} is refunded to your original payment method through Razorpay.`}
      </ConfirmDialog>

      <Modal
        open={Boolean(returnItem)}
        title="Return item"
        subtitle={returnItem?.productName}
        onClose={() => !busy && setReturnItem(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setReturnItem(null)} disabled={busy}>
              Cancel
            </button>
            <button type="submit" form="return-form" className="btn btn-ink" disabled={busy}>
              Request return
            </button>
          </>
        }
      >
        <form id="return-form" onSubmit={submitReturn} noValidate className="d-grid gap-3">
          {returnError && <Alert variant="danger">{returnError}</Alert>}
          <div>
            <label htmlFor="return-reason" className="form-label">
              Reason
            </label>
            <select id="return-reason" className="form-select" value={returnForm.reason} onChange={(e) => setReturnForm((f) => ({ ...f, reason: e.target.value }))}>
              <option value="">Choose a reason</option>
              {RETURN_REASONS.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="return-comments" className="form-label">
              Details (optional)
            </label>
            <textarea id="return-comments" className="form-control" rows={3} maxLength={1000} value={returnForm.comments}
              onChange={(e) => setReturnForm((f) => ({ ...f, comments: e.target.value }))} />
          </div>
          <p className="small text-muted mb-0">
            If approved you&apos;ll be refunded {returnItem && formatPrice(returnItem.lineTotal)} (less this item&apos;s share of any coupon).
          </p>
        </form>
      </Modal>
    </div>
  )
}
