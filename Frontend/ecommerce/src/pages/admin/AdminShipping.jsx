import { useCallback, useEffect, useState } from 'react'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import Modal from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field } from '../../components/FormControls'
import { Alert, EmptyState, ErrorState, PageHeader, PageLoader, Segment, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDate, formatDateTime, formatPrice, labelize } from '../../utils/format'

/** Shipping Management module — every order's shipment, and updating courier, tracking number and status. */
const FILTERS = [
  { value: '', label: 'All' },
  { value: 'PENDING', label: 'Not shipped' },
  { value: 'SHIPPED', label: 'Shipped' },
  { value: 'IN_TRANSIT', label: 'In transit' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
]
const COURIERS = ['ShopSphere Delivery', 'Delhivery', 'Blue Dart', 'DTDC', 'Ekart', 'India Post', 'Xpressbees']
const RANK = { PENDING: 0, SHIPPED: 1, IN_TRANSIT: 2, OUT_FOR_DELIVERY: 3, DELIVERED: 4 }
const TARGETS = ['SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED']
const ZONE = { METRO: 'Metro', NATIONAL: 'Rest of India', REMOTE: 'Remote' }

const statusLabel = (s) => (s === 'PENDING' ? 'Not shipped' : labelize(s))
const canUpdate = (s) => !['CANCELLED', 'RETURNED'].includes(s.shippingStatus) && s.orderStatus !== 'PENDING_PAYMENT'

export default function AdminShipping() {
  const toast = useToast()
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const debounced = useDebounce(search.trim(), 300)
  const [summary, setSummary] = useState(null)
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => adminService.getShipments({ status, search: debounced, page, size: 10 }),
    [status, debounced],
  )
  const [target, setTarget] = useState(null)
  const [form, setForm] = useState({ courierService: '', trackingNumber: '', status: '', note: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [busy, setBusy] = useState(false)

  const loadSummary = useCallback(() => {
    adminService.getShippingSummary().then(setSummary).catch(() => setSummary(null))
  }, [])
  useEffect(() => {
    loadSummary()
  }, [loadSummary])

  const open = (s) => {
    setTarget(s)
    setForm({
      courierService: s.courierService || 'ShopSphere Delivery',
      trackingNumber: s.trackingNumber || '',
      status: s.shippingStatus === 'PENDING' ? 'SHIPPED' : s.shippingStatus,
      note: '',
    })
    setErrors({})
    setFormError(null)
  }

  const save = async (event) => {
    event.preventDefault()
    const found = {}
    if (form.courierService.trim().length < 2) found.courierService = 'Choose or type the courier service'
    if (!/^[A-Za-z0-9-]{5,40}$/.test(form.trackingNumber.trim())) found.trackingNumber = 'Tracking number must be 5-40 letters, numbers or -'
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return
    setBusy(true)
    try {
      const updated = await adminService.updateShipping(target.orderId, {
        courierService: form.courierService.trim(),
        trackingNumber: form.trackingNumber.trim(),
        status: form.status,
        note: form.note.trim() || null,
      })
      setResult((r) => ({ ...r, content: r.content.map((x) => (x.orderId === updated.orderId ? updated : x)) }))
      toast.success(`Shipping for ${updated.orderNumber} updated — ${statusLabel(updated.shippingStatus)}, ${updated.courierService} ${updated.trackingNumber}.`)
      setTarget(null)
      loadSummary()
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  return (
    <>
      <PageHeader
        eyebrow="Operations"
        title="Shipping management"
        subtitle="Every order's shipment: courier, tracking number, status and shipping cost. Update shipping details once an order is shipped."
      />

      <div className="row g-3 mb-4">
        <div className="col-6 col-lg-3">
          <StatCard label="Not shipped yet" value={summary?.pending ?? '—'} icon="bi-hourglass-split" foot="Waiting to be handed to a courier" />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="On the way" value={summary ? summary.shipped + summary.inTransit + summary.outForDelivery : '—'} icon="bi-truck" foot={summary && `${summary.shipped} shipped · ${summary.inTransit} in transit · ${summary.outForDelivery} out for delivery`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Delivered" value={summary?.delivered ?? '—'} icon="bi-house-check" foot={summary && `${summary.cancelledOrReturned} cancelled or returned`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Shipping charged" value={summary ? formatPrice(summary.shippingRevenue) : '—'} icon="bi-cash-coin" dark foot={summary && `${summary.express} express order(s)`} />
        </div>
      </div>

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar flex-wrap">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input type="search" className="form-control form-control-sm" placeholder="Order, tracking number, courier, customer or city" aria-label="Search shipments" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <Segment label="Shipping status" options={FILTERS} value={status} onChange={setStatus} />
        </div>
        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-truck" title="No shipments found">
            No shipments match these filters.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Deliver to</th>
                  <th>Method &amp; cost</th>
                  <th>Courier &amp; tracking</th>
                  <th>Status</th>
                  <th className="text-end">Action</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((s) => (
                  <tr key={s.shippingId}>
                    <td>
                      <div className="ss-cell-title">{s.orderNumber}</div>
                      <div className="ss-cell-sub">{s.customerName}</div>
                      <div className="ss-cell-sub">{formatDateTime(s.createdAt)}</div>
                    </td>
                    <td className="small">
                      {s.city}, {s.state} {s.pincode}
                      <div className="ss-cell-sub">{ZONE[s.deliveryZone] || '—'} zone</div>
                    </td>
                    <td className="small text-nowrap">
                      <div className="fw-semibold">{s.shippingMethod === 'EXPRESS' ? 'Express' : 'Standard'}</div>
                      <div className="ss-cell-sub">
                        {Number(s.shippingCost) > 0 ? formatPrice(s.shippingCost) : 'Free'} · {Number(s.weightKg).toFixed(2)} kg
                      </div>
                    </td>
                    <td className="small">
                      {s.trackingNumber ? (
                        <>
                          <div className="fw-semibold">{s.trackingNumber}</div>
                          <div className="ss-cell-sub">{s.courierService}</div>
                        </>
                      ) : (
                        <span className="text-muted">Not shipped yet</span>
                      )}
                    </td>
                    <td>
                      <StatusBadge status={s.shippingStatus === 'PENDING' ? 'PLACED' : s.shippingStatus === 'IN_TRANSIT' ? 'SHIPPED' : s.shippingStatus} label={statusLabel(s.shippingStatus)} />
                      <div className="ss-cell-sub mt-1">
                        {s.deliveredAt ? `Delivered ${formatDate(s.deliveredAt)}` : s.estimatedDelivery && !['CANCELLED', 'RETURNED'].includes(s.shippingStatus) ? `Due ${formatDate(s.estimatedDelivery)}` : ''}
                      </div>
                    </td>
                    <td className="text-end text-nowrap">
                      {canUpdate(s) ? (
                        <button type="button" className="btn btn-sm btn-outline-ink" onClick={() => open(s)}>
                          <i className="bi bi-pencil me-1" aria-hidden="true" />
                          {s.shippingStatus === 'PENDING' ? 'Ship' : 'Update'}
                        </button>
                      ) : (
                        <span className="small text-muted">—</span>
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
        open={Boolean(target)}
        title={target && `${target.shippingStatus === 'PENDING' ? 'Ship' : 'Update shipping for'} ${target.orderNumber}`}
        subtitle={target && `${target.customerName} · ${target.city} ${target.pincode} · ${target.shippingMethod === 'EXPRESS' ? 'Express' : 'Standard'}`}
        onClose={() => !busy && setTarget(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setTarget(null)} disabled={busy}>
              Cancel
            </button>
            <button type="submit" form="shipping-form" className="btn btn-ink" disabled={busy}>
              {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              Save shipping details
            </button>
          </>
        }
      >
        {target && (
          <form id="shipping-form" onSubmit={save} noValidate className="d-grid gap-3">
            {formError && <Alert variant="danger">{formError}</Alert>}
            {target.shippingStatus === 'PENDING' && (
              <Alert variant="info" className="mb-0">
                This order has not been shipped yet. Saving marks it as shipped (the confirm and pack steps are recorded automatically) and notifies the customer.
              </Alert>
            )}
            <div>
              <Field id="courier" label="Courier service" value={form.courierService} onChange={set('courierService')} error={errors.courierService} list="courier-list" maxLength={100} />
              <datalist id="courier-list">
                {COURIERS.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </div>
            <Field id="tracking" label="Tracking number" value={form.trackingNumber} onChange={set('trackingNumber')} error={errors.trackingNumber} hint="From the courier, e.g. DLV1234567890 (5-40 letters, numbers or -)" maxLength={40} />
            <Field id="ship-status" as="select" label="Shipping status" value={form.status} onChange={set('status')}>
              {TARGETS.filter((t) => RANK[t] >= RANK[target.shippingStatus]).map((t) => (
                <option key={t} value={t}>
                  {statusLabel(t)}
                </option>
              ))}
            </Field>
            <Field id="ship-note" label="Note for the customer (optional)" value={form.note} onChange={set('note')} maxLength={255} />
            <p className="small text-muted mb-0">The customer is notified and can track the parcel with this number. Statuses only move forward.</p>
          </form>
        )}
      </Modal>
    </>
  )
}
