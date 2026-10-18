import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import sellerService from '../../services/sellerService'
import { useToast } from '../../context/ToastContext'
import { useLiveEvent, useLiveStatus } from '../../context/LiveContext'
import usePagedList from '../../hooks/usePagedList'
import Modal from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field } from '../../components/FormControls'
import { Alert, EmptyState, ErrorState, PageHeader, PageLoader, SafeImage, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDateTime, formatPrice, labelize } from '../../utils/format'

const FILTERS = ['', 'PLACED', 'CONFIRMED', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'RETURN_REQUESTED', 'RETURNED']

/** Button text for each next status a seller can choose. */
/** The only steps a seller takes; shipping and delivery are recorded by the delivery partner. */
const ACTION_LABELS = {
  CONFIRMED: 'Confirm',
  PACKED: 'Mark packed',
  CANCELLED: 'Cancel item',
}

/** What the seller sees once their part is done. */
const WAITING = {
  PACKED: 'Packed — waiting for a ShopSphere delivery partner to pick it up',
  SHIPPED: 'Picked up — the delivery partner is handling it',
  OUT_FOR_DELIVERY: 'Out for delivery with the delivery partner',
}

export default function SellerOrders() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const status = FILTERS.includes(params.get('status')) ? params.get('status') : ''
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => sellerService.getOrders({ status, page, size: 10 }),
    [status],
  )

  // New orders and pickups by delivery partners arrive live.
  const live = useLiveStatus()
  useLiveEvent((type) => {
    if (type === 'notification') reload()
  })

  // { item, status } — the update being confirmed in the dialog.
  const [pending, setPending] = useState(null)
  const [form, setForm] = useState({ note: '' })
  const [errors, setErrors] = useState({})
  const [busy, setBusy] = useState(false)

  const open = (item, next) => {
    setPending({ item, status: next })
    setForm({ note: '' })
    setErrors({})
  }

  const submit = async (event) => {
    event.preventDefault()
    const found = {}
    if (pending.status === 'CANCELLED' && !form.note.trim()) found.note = 'Tell the customer why the item is cancelled'
    setErrors(found)
    if (Object.keys(found).length) return

    setBusy(true)
    try {
      const updated = await sellerService.updateOrderItem(pending.item.orderItemId, {
        status: pending.status,
        note: form.note.trim() || null,
      })
      setResult((r) => ({ ...r, content: r.content.map((i) => (i.orderItemId === updated.orderItemId ? updated : i)) }))
      toast.success(`${updated.orderNumber}: ${updated.productName} is now ${labelize(updated.itemStatus).toLowerCase()}.`)
      setPending(null)
    } catch (err) {
      const fieldErrors = getFieldErrors(err)
      setErrors(Object.keys(fieldErrors).length ? fieldErrors : { form: getErrorMessage(err) })
    } finally {
      setBusy(false)
    }
  }

  const cancelling = pending?.status === 'CANCELLED'

  return (
    <>
      <PageHeader
        eyebrow="Fulfilment"
        title="Orders"
        subtitle="Each row is one of your products in a customer's order. Your part: Confirm → Mark packed. A ShopSphere delivery partner then picks it up, and only they can mark it out for delivery and delivered."
      />

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <span className={`ss-live-pill ${live ? 'on' : ''}`}>
            <span className="ss-live-dot" aria-hidden="true" />
            {live ? 'Live' : 'Offline'}
          </span>
          <label htmlFor="status-filter" className="small fw-semibold text-muted">
            Status
          </label>
          <select
            id="status-filter"
            className="form-select form-select-sm w-auto"
            value={status}
            onChange={(e) => setParams(e.target.value ? { status: e.target.value } : {}, { replace: true })}
          >
            {FILTERS.map((s) => (
              <option key={s} value={s}>
                {s ? labelize(s) : 'All statuses'}
              </option>
            ))}
          </select>
        </div>
        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-receipt" title={status ? 'No orders with this status' : 'No orders yet'}>
            {status ? 'Choose another status to see more orders.' : 'Orders containing your products will appear here.'}
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Product</th>
                  <th>Amount</th>
                  <th>Ship to</th>
                  <th>Status</th>
                  <th className="text-end">Next step</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((item) => (
                  <tr key={item.orderItemId}>
                    <td>
                      <div className="ss-cell-title">{item.orderNumber}</div>
                      <div className="ss-cell-sub">{formatDateTime(item.orderedAt)}</div>
                      <div className="ss-cell-sub">{item.paymentMethod === 'COD' ? 'Cash on delivery' : 'Paid online'}</div>
                    </td>
                    <td>
                      <div className="d-flex align-items-center gap-2">
                        <div className="ss-thumb">
                          <SafeImage src={item.imageUrl} alt="" />
                        </div>
                        <div className="min-w-0">
                          <div className="ss-cell-title">{item.productName}</div>
                          <div className="ss-cell-sub">
                            {item.quantity} × {formatPrice(item.unitPrice)}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="fw-semibold">{formatPrice(item.lineTotal)}</td>
                    <td className="small" style={{ minWidth: 200 }}>
                      <div className="fw-semibold">{item.shippingName}</div>
                      <div className="text-muted">{item.shippingAddress}</div>
                      <div className="text-muted">{item.shippingPhone}</div>
                    </td>
                    <td>
                      <StatusBadge status={item.itemStatus} />
                      {item.trackingNumber && (
                        <div className="ss-cell-sub mt-1">
                          {item.courierName} · {item.trackingNumber}
                        </div>
                      )}
                      {item.deliveredAt && <div className="ss-cell-sub">Delivered {formatDateTime(item.deliveredAt)}</div>}
                      {item.deliveryPartnerName && (
                        <div className="ss-cell-sub mt-1">
                          <i className="bi bi-bicycle me-1" aria-hidden="true" />
                          {item.deliveryPartnerName} · {item.deliveryPartnerPhone}
                        </div>
                      )}
                    </td>
                    <td className="text-end">
                      {WAITING[item.itemStatus] && (
                        <div className="small text-muted mb-1" style={{ maxWidth: 220, marginLeft: 'auto' }}>
                          {WAITING[item.itemStatus]}
                        </div>
                      )}
                      {item.nextStatuses.length === 0 ? (
                        !WAITING[item.itemStatus] && <span className="small text-muted">No action needed</span>
                      ) : (
                        <div className="d-flex flex-wrap gap-1 justify-content-end">
                          {item.nextStatuses
                            .filter((s) => s !== 'CANCELLED')
                            .map((s) => (
                              <button key={s} type="button" className="btn btn-sm btn-ink" onClick={() => open(item, s)}>
                                {ACTION_LABELS[s] || labelize(s)}
                              </button>
                            ))}
                          {item.nextStatuses.includes('CANCELLED') && (
                            <button type="button" className="btn btn-sm btn-danger-soft" onClick={() => open(item, 'CANCELLED')}>
                              Cancel
                            </button>
                          )}
                        </div>
                      )}
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
        open={Boolean(pending)}
        title={pending ? `${ACTION_LABELS[pending.status] || labelize(pending.status)} — ${pending.item.orderNumber}` : ''}
        subtitle={pending && `${pending.item.productName} × ${pending.item.quantity}`}
        onClose={() => !busy && setPending(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setPending(null)} disabled={busy}>
              Back
            </button>
            <button type="submit" form="fulfil-form" className={`btn ${cancelling ? 'btn-danger-soft' : 'btn-ink'}`} disabled={busy}>
              {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              {cancelling ? 'Cancel item' : `Mark ${labelize(pending?.status || '').toLowerCase()}`}
            </button>
          </>
        }
      >
        <form id="fulfil-form" onSubmit={submit} noValidate className="d-grid gap-3">
          {errors.form && <Alert variant="danger">{errors.form}</Alert>}
          {cancelling && (
            <Alert variant="warning">
              The item is restocked and the customer is refunded for it automatically if they paid online.
            </Alert>
          )}
          {pending?.status === 'PACKED' && (
            <Alert variant="info">Once packed, the parcel is offered to ShopSphere delivery partners for pickup. You can still cancel it until a partner picks it up.</Alert>
          )}
          <Field
            id="note"
            as="textarea"
            rows={2}
            label={cancelling ? 'Reason for cancelling' : 'Note to the customer (optional)'}
            maxLength={255}
            value={form.note}
            onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
            error={errors.note}
          />
        </form>
      </Modal>
    </>
  )
}
