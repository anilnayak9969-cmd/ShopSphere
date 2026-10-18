import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import Modal from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Alert, EmptyState, ErrorState, PageHeader, PageLoader, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDateTime, formatPrice, labelize } from '../../utils/format'

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

export default function AdminOrders() {
  const toast = useToast()
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const [params] = useSearchParams()
  const [status, setStatus] = useState(() => (STATUSES.includes(params.get('status')) ? params.get('status') : ''))
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const [selected, setSelected] = useState(null)
  const [nextStatus, setNextStatus] = useState('')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(() => {
    setError(null)
    adminService
      .getOrders({ status, search: debouncedSearch, page, size: 10 })
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
  }, [status, debouncedSearch, page])

  useEffect(() => {
    load()
  }, [load])

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
      toast.success(`${updated.orderNumber} is now ${labelize(updated.status)}.`)
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

  return (
    <>
      <PageHeader eyebrow="Operations" title="Orders" subtitle="Sellers fulfil their own items; use this page to monitor orders, see each item's tracking, and step in when needed." />

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Order number or customer email"
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
            aria-label="Filter by status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value)
              setPage(0)
            }}
          >
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {labelize(s)}
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
            Orders appear here once customers check out.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Total</th>
                  <th>Payment</th>
                  <th>Status</th>
                  <th className="text-end">Action</th>
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
                    <td className="fw-semibold">{formatPrice(o.totalAmount)}</td>
                    <td>
                      <StatusBadge status={o.paymentStatus} label={o.paymentMethod === 'COD' && o.paymentStatus === 'PENDING' ? 'COD · due' : undefined} />
                      <div className="ss-cell-sub">{o.paymentMethod === 'COD' ? 'Cash on delivery' : 'Razorpay'}</div>
                    </td>
                    <td>
                      <StatusBadge status={o.status} />
                    </td>
                    <td className="text-end">
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => open(o.orderId)}>
                        Manage
                      </button>
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
        title={selected?.orderNumber}
        subtitle={selected && `${selected.customerName} · ${formatDateTime(selected.createdAt)}`}
        onClose={() => !saving && setSelected(null)}
      >
        {selected && (
          <>
            <div className="d-flex gap-2 mb-3 flex-wrap align-items-center">
              <StatusBadge status={selected.status} />
              <StatusBadge status={selected.paymentStatus} />
              <span className="small text-muted">{selected.paymentMethod === 'COD' ? 'Cash on delivery' : 'Paid with Razorpay'}</span>
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
                        <StatusBadge status={item.itemStatus} />
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
                {Number(selected.discountAmount) > 0 && (
                  <div className="ss-summary-row mt-2">
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
              </div>
              <div className="col-md-5">
                <h3 className="h5">Ship to</h3>
                <p className="small text-muted">
                  {selected.shippingName}, {selected.shippingPhone}
                  <br />
                  {selected.addressLine1}
                  {selected.addressLine2 && `, ${selected.addressLine2}`}
                  <br />
                  {selected.city}, {selected.state} {selected.pincode}
                </p>
              </div>
            </div>

            <div className="ss-divider" />
            {selected.nextStatuses.length === 0 ? (
              <p className="text-muted small mb-0">This order is final — no further status changes are possible.</p>
            ) : (
              <div className="row g-2 align-items-end">
                <div className="col-md-4">
                  <label htmlFor="next-status" className="form-label">
                    Move to
                  </label>
                  <select id="next-status" className="form-select" value={nextStatus} onChange={(e) => setNextStatus(e.target.value)}>
                    <option value="">Choose status</option>
                    {selected.nextStatuses.map((s) => (
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
                  <button type="button" className={`btn w-100 ${nextStatus === 'CANCELLED' ? 'btn-danger-soft' : 'btn-ink'}`} disabled={!nextStatus || saving} onClick={save}>
                    {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                    Update
                  </button>
                </div>
                {nextStatus === 'CANCELLED' && selected.paymentStatus === 'SUCCESS' && (
                  <div className="col-12">
                    <Alert variant="warning">Cancelling restocks every open item and refunds the customer for them through Razorpay.</Alert>
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </Modal>
    </>
  )
}
