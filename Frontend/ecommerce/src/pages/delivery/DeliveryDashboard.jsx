import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import deliveryService from '../../services/deliveryService'
import { useToast } from '../../context/ToastContext'
import { useLiveEvent, useLiveStatus } from '../../context/LiveContext'
import usePagedList from '../../hooks/usePagedList'
import { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Alert, EmptyState, ErrorState, PageHeader, PageLoader, SafeImage, Segment, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDateTime, formatPrice, labelize } from '../../utils/format'

const TABS = [
  { value: 'pickups', label: 'Available pickups' },
  { value: 'active', label: 'My deliveries' },
  { value: 'completed', label: 'Delivered' },
]

const ACTION = {
  OUT_FOR_DELIVERY: { label: 'Out for delivery', icon: 'bi-truck' },
  DELIVERED: { label: 'Mark delivered', icon: 'bi-check2-circle' },
}

/** One parcel: pickup → drop, with the action this partner can take next. */
function JobCard({ job, onAccept, onAdvance, canAccept, busy }) {
  const assigned = job.dropName != null
  return (
    <article className="ss-card ss-card-pad">
      <div className="d-flex gap-3 align-items-start">
        <div className="ss-thumb" style={{ width: 64, height: 64 }}>
          <SafeImage src={job.imageUrl} alt="" width={160} />
        </div>
        <div className="flex-grow-1 min-w-0">
          <div className="d-flex justify-content-between gap-2 flex-wrap">
            <div className="min-w-0">
              <div className="fw-semibold text-truncate">{job.productName}</div>
              <div className="small text-muted">
                {job.orderNumber} · qty {job.quantity} · {job.storeName}
              </div>
            </div>
            <div className="text-end">
              <StatusBadge status={job.itemStatus} label={job.itemStatus === 'PACKED' ? 'Ready for pickup' : undefined} />
              {job.inYourCity && !assigned && <div className="small text-success fw-semibold mt-1">In your city</div>}
            </div>
          </div>

          <div className="row g-3 mt-1 small">
            <div className="col-md-6">
              <div className="ss-eyebrow mb-1">
                <i className="bi bi-shop me-1" aria-hidden="true" />
                Pickup
              </div>
              <div className="fw-semibold">{job.storeName}</div>
              <div className="text-muted">
                {[job.pickupAddress, job.pickupCity, job.pickupPincode].filter(Boolean).join(', ') || 'Address shared by the seller'}
              </div>
              {job.pickupPhone && <div className="text-muted">{job.pickupPhone}</div>}
            </div>
            <div className="col-md-6">
              <div className="ss-eyebrow mb-1">
                <i className="bi bi-geo-alt me-1" aria-hidden="true" />
                Drop
              </div>
              {assigned ? (
                <>
                  <div className="fw-semibold">{job.dropName}</div>
                  <div className="text-muted">
                    {job.dropAddress}, {job.dropCity} {job.dropPincode}
                  </div>
                  <a href={`tel:${job.dropPhone}`}>{job.dropPhone}</a>
                </>
              ) : (
                <>
                  <div className="fw-semibold">
                    {job.dropCity} {job.dropPincode}
                  </div>
                  <div className="text-muted">Customer details appear after you accept.</div>
                </>
              )}
            </div>
          </div>

          <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mt-3 pt-3 border-top">
            <div className="small">
              {job.paymentMethod === 'COD' ? (
                <span className="fw-semibold text-warning-emphasis">
                  <i className="bi bi-cash-coin me-1" aria-hidden="true" />
                  Cash on delivery — collect {formatPrice(job.cashToCollect)}
                </span>
              ) : (
                <span className="text-muted">
                  <i className="bi bi-credit-card me-1" aria-hidden="true" />
                  Prepaid · item value {formatPrice(job.itemValue)}
                </span>
              )}
              {job.trackingNumber && <span className="text-muted"> · {job.trackingNumber}</span>}
              {job.deliveredAt && <span className="text-muted"> · delivered {formatDateTime(job.deliveredAt)}</span>}
            </div>
            <div className="d-flex gap-2">
              {!assigned && (
                <button type="button" className="btn btn-ink btn-sm" disabled={!canAccept || busy} onClick={() => onAccept(job)}>
                  <i className="bi bi-box-arrow-in-down me-1" aria-hidden="true" />
                  Accept pickup
                </button>
              )}
              {job.nextStatuses.map((s) => (
                <button key={s} type="button" className="btn btn-ink btn-sm" disabled={busy} onClick={() => onAdvance(job, s)}>
                  <i className={`bi ${ACTION[s]?.icon} me-1`} aria-hidden="true" />
                  {ACTION[s]?.label || labelize(s)}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </article>
  )
}

/** Delivery partner home: live counts, parcels to pick up, deliveries in progress and history. */
export default function DeliveryDashboard() {
  const toast = useToast()
  const [tab, setTab] = useState('pickups')
  const [dash, setDash] = useState(null)
  const [dashError, setDashError] = useState(null)
  const [confirm, setConfirm] = useState(null) // { job, status }
  const [busy, setBusy] = useState(false)

  const loadDash = useCallback(() => {
    setDashError(null)
    deliveryService
      .getDashboard()
      .then(setDash)
      .catch((err) => setDashError(getErrorMessage(err)))
  }, [])

  useEffect(() => {
    loadDash()
  }, [loadDash])

  const { result, error, reload, setPage } = usePagedList(
    (page) => (tab === 'pickups' ? deliveryService.getPickups(page, 10) : deliveryService.getJobs(tab, page, 10)),
    [tab],
  )

  const refresh = () => {
    reload()
    loadDash()
  }

  // A seller packed a parcel, or another partner took one: update the lists without a click.
  const live = useLiveStatus()
  useLiveEvent((type) => {
    if (type === 'pickups' || type === 'notification') {
      reload()
      deliveryService.getDashboard().then(setDash).catch(() => {})
    }
  })

  const accept = async (job) => {
    setBusy(true)
    try {
      await deliveryService.accept(job.orderItemId)
      toast.success(`Accepted ${job.orderNumber}. Pick it up from ${job.storeName}.`)
      setTab('active')
      loadDash()
    } catch (err) {
      toast.error(getErrorMessage(err))
      refresh()
    } finally {
      setBusy(false)
    }
  }

  const advance = async () => {
    const { job, status } = confirm
    setBusy(true)
    try {
      await deliveryService.updateStatus(job.orderItemId, status)
      toast.success(`${job.orderNumber}: ${labelize(status).toLowerCase()}.`)
      setConfirm(null)
      refresh()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (dashError) return <ErrorState message={dashError} onRetry={loadDash} />
  if (!dash) return <PageLoader />

  const approved = dash.partner.status === 'APPROVED' && dash.partner.active

  return (
    <>
      <PageHeader
        eyebrow="Delivery partner"
        title={`Hi, ${dash.partner.fullName.split(' ')[0]}`}
        subtitle={`Delivering in ${dash.partner.serviceCity} ${dash.partner.servicePincode}. Packed parcels appear below — accept one, pick it up from the store and deliver it.`}
        actions={<StatusBadge status={dash.partner.status} label={`Application ${labelize(dash.partner.status).toLowerCase()}`} />}
      />
      {!approved && (
        <Alert variant="warning" className="mb-4">
          You can browse pickups, but you can accept them only after your application is approved. See <Link to="/delivery/profile">Profile</Link>.
        </Alert>
      )}

      <div className="row g-3 mb-4">
        <div className="col-6 col-xl-3">
          <StatCard label="Ready for pickup" value={dash.availablePickups} icon="bi-box-seam" foot="Packed by sellers" />
        </div>
        <div className="col-6 col-xl-3">
          <StatCard dark label="On the way" value={dash.activeDeliveries} icon="bi-truck" foot="Accepted by you" />
        </div>
        <div className="col-6 col-xl-3">
          <StatCard label="Delivered today" value={dash.deliveredToday} icon="bi-check2-circle" />
        </div>
        <div className="col-6 col-xl-3">
          <StatCard label="Delivered in total" value={dash.deliveredTotal} icon="bi-trophy" />
        </div>
      </div>

      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <Segment label="Deliveries" options={TABS} value={tab} onChange={setTab} />
        <div className="d-flex align-items-center gap-2">
          <span className={`ss-live-pill ${live ? 'on' : ''}`}>
            <span className="ss-live-dot" aria-hidden="true" />
            {live ? 'Live' : 'Offline'}
          </span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={refresh}>
            <i className="bi bi-arrow-clockwise me-1" aria-hidden="true" />
            Refresh
          </button>
        </div>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !result ? (
        <PageLoader />
      ) : result.content.length === 0 ? (
        <div className="ss-card">
          <EmptyState icon={tab === 'pickups' ? 'bi-box-seam' : 'bi-truck'} title={tab === 'pickups' ? 'No parcels waiting' : tab === 'active' ? 'Nothing on the way' : 'No deliveries yet'}>
            {tab === 'pickups'
              ? 'When a seller marks an item as packed, it appears here for pickup.'
              : tab === 'active'
                ? 'Accept a pickup to start a delivery.'
                : 'Parcels you deliver are listed here.'}
          </EmptyState>
        </div>
      ) : (
        <div className="d-grid gap-3">
          {result.content.map((job) => (
            <JobCard key={job.orderItemId} job={job} canAccept={approved} busy={busy} onAccept={accept} onAdvance={(j, s) => setConfirm({ job: j, status: s })} />
          ))}
        </div>
      )}
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}

      <ConfirmDialog
        open={Boolean(confirm)}
        title={confirm?.status === 'DELIVERED' ? 'Mark as delivered?' : 'Out for delivery?'}
        confirmLabel={ACTION[confirm?.status]?.label}
        busy={busy}
        onConfirm={advance}
        onCancel={() => setConfirm(null)}
      >
        {confirm?.status === 'DELIVERED'
          ? `Confirm you handed ${confirm?.job.productName} to ${confirm?.job.dropName}${confirm?.job.paymentMethod === 'COD' ? ` and collected ${formatPrice(confirm?.job.cashToCollect)}` : ''}. The customer is notified.`
          : `The customer will be told ${confirm?.job.productName} is out for delivery today.`}
      </ConfirmDialog>
    </>
  )
}
