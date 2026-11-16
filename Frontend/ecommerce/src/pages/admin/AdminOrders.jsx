import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import Modal, { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Alert, EmptyState, ErrorState, PageHeader, PageLoader, Segment, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDateTime, formatPrice, labelize } from '../../utils/format'

/**
 * Order Management module — the admin Order dashboard.
 * View every customer order with its customer, shipping address and status, filter by
 * Pending / Shipped / Delivered / Cancelled, update the status, and cancel orders that have not shipped.
 */
const GROUPS = [
  { value: '', label: 'All orders' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'SHIPPED', label: 'Shipped' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'RETURNS', label: 'Returns' },
]

const STATUSES = [
  'PLACED',
  'CONFIRMED',
  'PACKED',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLATION_REQUESTED',
  'CANCELLED',
  'RETURN_REQUESTED',
  'RETURNED',
  'REFUNDED',
  'PENDING_PAYMENT',
]

/** Normal forward path. An admin can jump ahead (e.g. Pending → Shipped); the steps in between are recorded. */
const PATH = ['PLACED', 'CONFIRMED', 'PROCESSING', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED']
const FORWARD = ['CONFIRMED', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED']
const NOT_SHIPPED = ['PLACED', 'CONFIRMED', 'PROCESSING', 'PACKED', 'CANCELLATION_REQUESTED']

/** Status choices the admin gets for an order in a given status. */
function statusChoices(order) {
  const at = PATH.indexOf(order.status)
  if (at >= 0) return FORWARD.filter((s) => PATH.indexOf(s) > at)
  // Off the normal path (e.g. a customer asked to cancel): use the server's allowed moves.
  return order.nextStatuses.filter((s) => s !== 'CANCELLED' && FORWARD.includes(s))
}

const canCancel = (order) => NOT_SHIPPED.includes(order.status)

/** Friendly name for the dashboard: "Placed" orders are what the assignment calls "Pending". */
const statusLabel = (status) => (status === 'PLACED' ? 'Pending' : undefined)

/** Payment in plain words, e.g. "COD · due", "Razorpay · paid". */
function paymentLabel(order) {
  const method = order.paymentMethod === 'COD' ? 'COD' : 'Razorpay'
  const state = {
    CREATED: 'awaiting payment',
    SUCCESS: Number(order.refundedAmount) > 0 ? 'part refunded' : 'paid',
    PENDING: order.paymentMethod === 'COD' ? 'due on delivery' : 'awaiting payment',
    REFUNDED: 'refunded',
    FAILED: 'failed',
    CANCELLED: 'not charged',
  }[order.paymentStatus]
  return state ? `${method} · ${state}` : method
}

export default function AdminOrders() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const rawGroup = params.get('group')
  const group = GROUPS.some((g) => g.value === rawGroup) ? rawGroup : ''
  const [status, setStatus] = useState(() => (STATUSES.includes(params.get('status')) ? params.get('status') : ''))
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [summary, setSummary] = useState(null)

  const [selected, setSelected] = useState(null)
  const [nextStatus, setNextStatus] = useState('')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const [cancelTarget, setCancelTarget] = useState(null)

  const load = useCallback(() => {
    setError(null)
    adminService
      .getOrders({ group, status, search: debouncedSearch, page, size: 10 })
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
    adminService.getOrderSummary().then(setSummary).catch(() => setSummary(null))
  }, [group, status, debouncedSearch, page])

  useEffect(() => {
    load()
  }, [load])

  const chooseGroup = (value) => {
    setStatus('')
    setPage(0)
    setParams(value ? { group: value } : {}, { replace: true })
  }

  const open = async (orderId) => {
    setNextStatus('')
    setNote('')
    try {
      setSelected(await adminService.getOrder(orderId))
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  const save = async () => {
    setSaving(true)
    try {
      const updated = await adminService.setOrderStatus(selected.orderId, nextStatus, note.trim() || null)
      toast.success(`Order ${updated.orderNumber} is now ${labelize(updated.status)}.`)
      setSelected(updated)
      setNextStatus('')
      setNote('')
      load()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const cancelOrder = async () => {
    setSaving(true)
    try {
      const updated = await adminService.setOrderStatus(cancelTarget.orderId, 'CANCELLED', note.trim() || 'Cancelled by ShopSphere')
      toast.success(`Order ${updated.orderNumber} has been cancelled.`)
      if (selected?.orderId === updated.orderId) setSelected(updated)
      setCancelTarget(null)
      setNote('')
      load()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const choices = selected ? statusChoices(selected) : []

  return (
    <>
      <PageHeader
        eyebrow="Operations"
        title="Order management"
        subtitle="Every customer order with its shipping address and status. Update the status as orders move, and cancel orders that have not shipped."
      />

      <div className="row g-3 mb-4">
        <div className="col-6 col-lg">
          <StatCard label="Total orders" value={summary?.total ?? '—'} icon="bi-receipt" foot="All placed orders" />
        </div>
        <div className="col-6 col-lg">
          <StatCard label="Pending" value={summary?.pending ?? '—'} icon="bi-hourglass-split" foot="Not shipped yet" />
        </div>
        <div className="col-6 col-lg">
          <StatCard label="Shipped" value={summary?.shipped ?? '—'} icon="bi-truck" foot="On the way" />
        </div>
        <div className="col-6 col-lg">
          <StatCard label="Delivered" value={summary?.delivered ?? '—'} icon="bi-box2-heart" foot="Completed" />
        </div>
        <div className="col-6 col-lg">
          <StatCard label="Cancelled" value={summary?.cancelled ?? '—'} icon="bi-x-circle" foot="Soft deleted, kept for history" />
        </div>
      </div>

      <div className="mb-3">
        <Segment label="Order status" options={GROUPS} value={group} onChange={chooseGroup} />
      </div>

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Order ID, customer name, email or city"
              aria-label="Search orders"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
            />
          </div>
          <select
            className="form-select form-select-sm ms-auto"
            style={{ maxWidth: 220 }}
            aria-label="Filter by exact status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value)
              setPage(0)
            }}
          >
            <option value="">Any exact status</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s === 'PLACED' ? 'Pending (placed)' : labelize(s)}
              </option>
            ))}
          </select>
        </div>

        {error ? (
          <ErrorState message={error} onRetry={load} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-receipt" title="No orders found">
            No orders match these filters.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Customer</th>
                  <th>Shipping address</th>
                  <th>Total &amp; payment</th>
                  <th>Status</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((o) => (
                  <tr key={o.orderId}>
                    <td>
                      <div className="ss-cell-title">{o.orderNumber}</div>
                      <div className="ss-cell-sub">{formatDateTime(o.createdAt)}</div>
                    </td>
                    <td>
                      <div>{o.customerName}</div>
                      <div className="ss-cell-sub">{o.customerEmail}</div>
                    </td>
                    <td style={{ minWidth: 190, maxWidth: 240 }}>
                      <div className="small">{o.shippingAddress}</div>
                    </td>
                    <td className="text-nowrap">
                      <div className="fw-semibold">{formatPrice(o.totalAmount)}</div>
                      <div className="ss-cell-sub">
                        {o.itemCount} item{o.itemCount === 1 ? '' : 's'} · {paymentLabel(o)}
                      </div>
                    </td>
                    <td>
                      <StatusBadge status={o.status} label={statusLabel(o.status)} />
                    </td>
                    <td className="text-end">
                      <div className="d-flex flex-column align-items-end gap-1">
                        <button type="button" className="btn btn-sm btn-outline-ink text-nowrap" onClick={() => open(o.orderId)}>
                          View / update
                        </button>
                        {canCancel(o) && (
                          <button type="button" className="btn btn-sm btn-danger-soft" onClick={() => setCancelTarget(o)}>
                            Cancel
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}

      <Modal
        open={Boolean(selected)}
        size="lg"
        title={selected && `Order ${selected.orderNumber}`}
        subtitle={selected && `${selected.customerName} · placed ${formatDateTime(selected.createdAt)}`}
        onClose={() => !saving && setSelected(null)}
      >
        {selected && (
          <>
            <div className="d-flex gap-2 mb-3 flex-wrap align-items-center">
              <span className="small text-muted">Order status</span>
              <StatusBadge status={selected.status} label={statusLabel(selected.status)} />
              <span className="small text-muted ms-2">Payment: {paymentLabel(selected)}</span>
              {Number(selected.refundedAmount) > 0 && <span className="small text-success fw-semibold">Refunded {formatPrice(selected.refundedAmount)}</span>}
            </div>
            {selected.statusNote && (
              <Alert variant="info" className="mb-3">
                {selected.statusNote}
              </Alert>
            )}
            <div className="row g-4">
              <div className="col-md-7">
                <h3 className="h5">Items</h3>
                {selected.items.map((item) => (
                  <div key={item.orderItemId} className="d-flex justify-content-between gap-2 small py-2 border-bottom">
                    <div className="min-w-0">
                      <div className="fw-semibold">
                        {item.productName} × {item.quantity}
                      </div>
                      <div className="text-muted">{item.storeName}</div>
                      <div className="d-flex gap-1 flex-wrap mt-1">
                        <StatusBadge status={item.itemStatus} label={statusLabel(item.itemStatus)} />
                        {item.returnStatus && <StatusBadge status={item.returnStatus} label={`Return ${labelize(item.returnStatus).toLowerCase()}`} />}
                      </div>
                      {item.trackingNumber && (
                        <div className="text-muted mt-1">
                          <i className="bi bi-truck me-1" aria-hidden="true" />
                          {item.courierName} · {item.trackingNumber}
                        </div>
                      )}
                    </div>
                    <span className="fw-semibold text-nowrap">{formatPrice(item.lineTotal)}</span>
                  </div>
                ))}
                <div className="ss-summary-row mt-2">
                  <span>Items</span>
                  <span>{formatPrice(selected.subtotal)}</span>
                </div>
                {Number(selected.discountAmount) > 0 && (
                  <div className="ss-summary-row">
                    <span>Coupon {selected.couponCode}</span>
                    <span className="text-success">− {formatPrice(selected.discountAmount)}</span>
                  </div>
                )}
                <div className="ss-summary-row">
                  <span>Shipping</span>
                  <span>{Number(selected.shippingFee) > 0 ? formatPrice(selected.shippingFee) : 'Free'}</span>
                </div>
                <div className="ss-summary-row ss-summary-total mt-2">
                  <span>Total</span>
                  <span>{formatPrice(selected.totalAmount)}</span>
                </div>
                <div className="ss-summary-row small text-muted">
                  <span>Includes GST (18%)</span>
                  <span>{formatPrice(selected.taxAmount)}</span>
                </div>
              </div>
              <div className="col-md-5">
                <h3 className="h5">Shipping address</h3>
                <p className="small text-muted">
                  {selected.shippingName}, {selected.shippingPhone}
                  <br />
                  {selected.addressLine1}
                  {selected.addressLine2 && `, ${selected.addressLine2}`}
                  <br />
                  {selected.city}, {selected.state} {selected.pincode}
                </p>
                <h3 className="h6 mt-3">Customer</h3>
                <p className="small text-muted mb-0">
                  {selected.customerName}
                  <br />
                  {selected.customerEmail}
                </p>
              </div>
            </div>

            <div className="ss-divider" />
            <h3 className="h5">Update order status</h3>
            {choices.length === 0 && !canCancel(selected) ? (
              <p className="text-muted small mb-0">This order is final — no further status changes are possible here.</p>
            ) : (
              <div className="row g-2 align-items-end">
                {choices.length > 0 && (
                  <>
                    <div className="col-md-4">
                      <label htmlFor="next-status" className="form-label">
                        Move to
                      </label>
                      <select id="next-status" className="form-select" value={nextStatus} onChange={(e) => setNextStatus(e.target.value)}>
                        <option value="">Choose status</option>
                        {choices.map((s) => (
                          <option key={s} value={s}>
                            {labelize(s)}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="col-md-5">
                      <label htmlFor="status-note" className="form-label">
                        Note for customer (optional)
                      </label>
                      <input id="status-note" className="form-control" maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} />
                    </div>
                    <div className="col-md-3">
                      <button type="button" className="btn btn-ink w-100" disabled={!nextStatus || saving} onClick={save}>
                        {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                        Update status
                      </button>
                    </div>
                  </>
                )}
                {canCancel(selected) && (
                  <div className="col-12 d-flex justify-content-between align-items-center flex-wrap gap-2 mt-2">
                    <span className="small text-muted">Not shipped yet, so this order can still be cancelled.</span>
                    <button type="button" className="btn btn-sm btn-danger-soft" onClick={() => setCancelTarget(selected)}>
                      Cancel order
                    </button>
                  </div>
                )}
                {nextStatus && PATH.indexOf(nextStatus) - PATH.indexOf(selected.status) > 1 && PATH.indexOf(selected.status) >= 0 && (
                  <div className="col-12">
                    <Alert variant="info" className="mb-0">
                      The steps in between (for example Confirmed and Packed) are recorded automatically in the tracking history.
                    </Alert>
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </Modal>

      <ConfirmDialog
        open={Boolean(cancelTarget)}
        title={`Cancel order ${cancelTarget?.orderNumber ?? ''}?`}
        confirmLabel="Cancel order"
        danger
        busy={saving}
        onConfirm={cancelOrder}
        onCancel={() => setCancelTarget(null)}
      >
        <Alert variant="warning" className="mb-0">
          <strong>Warning:</strong> the order&apos;s status becomes <strong>Cancelled</strong> and every item goes back into stock.
          {cancelTarget?.paymentMethod === 'COD'
            ? ' Nothing is collected for this Cash on Delivery order.'
            : ` ${formatPrice(cancelTarget?.totalAmount)} is refunded to the customer through Razorpay.`}{' '}
          The customer and sellers are notified. The order is kept in the database (soft delete) and cannot be reopened.
        </Alert>
      </ConfirmDialog>
    </>
  )
}
