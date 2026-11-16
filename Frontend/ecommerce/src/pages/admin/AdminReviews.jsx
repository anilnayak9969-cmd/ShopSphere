import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import Modal, { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field } from '../../components/FormControls'
import { Stars } from '../../components/Rating'
import { Alert, EmptyState, ErrorState, PageHeader, PageLoader, Segment, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDate } from '../../utils/format'

/** Review and Rating Management — moderation: approve, reject (with a reason) or delete customer reviews. */
const STATUS_FILTERS = [
  { value: 'PENDING', label: 'Awaiting approval' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: '', label: 'All' },
]
const RATING_FILTERS = [{ value: '', label: 'Any ★' }, ...[5, 4, 3, 2, 1].map((n) => ({ value: String(n), label: `${n} ★` }))]
const STATUS_LABEL = { PENDING: 'Awaiting approval', APPROVED: 'Approved', REJECTED: 'Rejected' }
const REASONS = [
  'Not about the product',
  'Offensive or abusive language',
  'Contains personal or contact details',
  'Spam or advertising',
]

export default function AdminReviews() {
  const toast = useToast()
  const [status, setStatus] = useState('PENDING')
  const [rating, setRating] = useState('')
  const [search, setSearch] = useState('')
  const debounced = useDebounce(search.trim(), 300)
  const [summary, setSummary] = useState(null)
  const { result, error, reload, setPage } = usePagedList(
    (page) => adminService.getReviews({ status, rating, search: debounced, page, size: 10 }),
    [status, rating, debounced],
  )
  const [busyId, setBusyId] = useState(null)
  const [rejecting, setRejecting] = useState(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState(null)
  const [removing, setRemoving] = useState(null)
  const [busy, setBusy] = useState(false)

  const loadSummary = useCallback(() => {
    adminService.getReviewSummary().then(setSummary).catch(() => setSummary(null))
  }, [])
  useEffect(() => {
    loadSummary()
  }, [loadSummary])

  const refresh = () => {
    reload()
    loadSummary()
  }

  const approve = async (r) => {
    setBusyId(r.reviewId)
    try {
      await adminService.approveReview(r.reviewId)
      toast.success(`Review of ${r.productName} approved — it is now on the product page.`)
      refresh()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  const openReject = (r) => {
    setRejecting(r)
    setReason(r.rejectionReason || '')
    setReasonError(null)
  }

  const reject = async (event) => {
    event.preventDefault()
    if (reason.trim().length < 3) {
      setReasonError('Give a reason for rejecting the review (3-255 characters)')
      return
    }
    setBusy(true)
    try {
      await adminService.rejectReview(rejecting.reviewId, reason.trim())
      toast.info(`Review of ${rejecting.productName} rejected. The customer was told why.`)
      setRejecting(null)
      refresh()
    } catch (err) {
      setReasonError(getFieldErrors(err).reason || getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    setBusy(true)
    try {
      await adminService.deleteReview(removing.reviewId)
      toast.success('Review deleted and the product rating recalculated.')
      setRemoving(null)
      refresh()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Moderation"
        title="Reviews & ratings"
        subtitle="Only customers whose item was delivered can review. New and edited reviews wait here; only approved reviews are shown and count towards a product's rating."
      />

      <div className="row g-3 mb-4">
        <div className="col-6 col-lg-3">
          <StatCard label="Awaiting approval" value={summary?.pending ?? '—'} icon="bi-hourglass-split" foot="New or edited reviews to check" />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Approved" value={summary?.approved ?? '—'} icon="bi-check2-circle" foot="Shown on product pages" />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Rejected" value={summary?.rejected ?? '—'} icon="bi-x-circle" foot="Hidden; the customer was told why" />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Average rating" value={summary ? `${Number(summary.averageRating).toFixed(1)} ★` : '—'} icon="bi-star-half" dark foot={summary && `Across ${summary.approved} approved review(s)`} />
        </div>
      </div>

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar flex-wrap">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input type="search" className="form-control form-control-sm" placeholder="Product, customer, email or review text" aria-label="Search reviews" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <Segment label="Review status" options={STATUS_FILTERS} value={status} onChange={setStatus} />
          <Segment label="Rating" options={RATING_FILTERS} value={rating} onChange={setRating} />
        </div>
        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-chat-square-quote" title={status === 'PENDING' ? 'Nothing to moderate' : 'No reviews'}>
            {status === 'PENDING' ? 'New and edited reviews appear here for approval.' : 'No review matches these filters.'}
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table align-middle">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Rating &amp; review</th>
                  <th>Customer</th>
                  <th>Status</th>
                  <th className="text-end">Action</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((r) => (
                  <tr key={r.reviewId}>
                    <td style={{ minWidth: 200 }}>
                      <div className="d-flex align-items-center gap-2">
                        {r.productImage && <img src={r.productImage} alt="" width={44} height={44} className="rounded object-fit-cover" />}
                        <Link to={`/products/${r.productId}`} className="ss-cell-title text-decoration-none">
                          {r.productName}
                        </Link>
                      </div>
                    </td>
                    <td style={{ maxWidth: 380 }}>
                      <Stars value={r.rating} showValue={false} />
                      {r.title && <div className="fw-semibold mt-1">{r.title}</div>}
                      <div className="ss-cell-sub" style={{ whiteSpace: 'pre-line' }}>
                        {r.comment || <span className="text-muted">No written review</span>}
                      </div>
                    </td>
                    <td className="small">
                      <div className="fw-semibold">{r.customerName}</div>
                      <div className="text-muted">{r.customerEmail}</div>
                      <div className="text-muted">{formatDate(r.updatedAt || r.createdAt)}</div>
                    </td>
                    <td className="small" style={{ maxWidth: 200 }}>
                      <StatusBadge status={r.status} label={STATUS_LABEL[r.status]} />
                      {r.status === 'REJECTED' && r.rejectionReason && <div className="text-muted mt-1">{r.rejectionReason}</div>}
                      {r.moderatedAt && r.status !== 'PENDING' && <div className="text-muted mt-1">{formatDate(r.moderatedAt)}</div>}
                    </td>
                    <td className="text-end text-nowrap">
                      {r.status !== 'APPROVED' && (
                        <button type="button" className="btn btn-sm btn-ink me-1" onClick={() => approve(r)} disabled={busyId === r.reviewId}>
                          <i className="bi bi-check2 me-1" aria-hidden="true" />
                          Approve
                        </button>
                      )}
                      {r.status !== 'REJECTED' && (
                        <button type="button" className="btn btn-sm btn-outline-ink me-1" onClick={() => openReject(r)} disabled={busyId === r.reviewId}>
                          <i className="bi bi-x-lg me-1" aria-hidden="true" />
                          Reject
                        </button>
                      )}
                      <button type="button" className="btn btn-sm btn-danger-soft" onClick={() => setRemoving(r)} aria-label={`Delete review of ${r.productName}`}>
                        <i className="bi bi-trash" aria-hidden="true" />
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
        open={Boolean(rejecting)}
        title="Reject this review?"
        subtitle={rejecting && `${rejecting.customerName} · ${rejecting.productName}`}
        onClose={() => !busy && setRejecting(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setRejecting(null)} disabled={busy}>
              Cancel
            </button>
            <button type="submit" form="reject-form" className="btn btn-danger" disabled={busy}>
              {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              Reject review
            </button>
          </>
        }
      >
        {rejecting && (
          <form id="reject-form" onSubmit={reject} noValidate className="d-grid gap-3">
            <Alert variant="info" className="mb-0">
              The review is hidden from the product page and does not count towards its rating. The customer sees the reason and can edit and resubmit it.
            </Alert>
            <div className="d-flex flex-wrap gap-2" role="group" aria-label="Common reasons">
              {REASONS.map((x) => (
                <button type="button" key={x} className={`btn btn-sm ${reason === x ? 'btn-ink' : 'btn-outline-ink'}`} onClick={() => setReason(x)}>
                  {x}
                </button>
              ))}
            </div>
            <Field id="reject-reason" label="Reason shown to the customer" value={reason} onChange={(e) => setReason(e.target.value)} error={reasonError} maxLength={255} />
          </form>
        )}
      </Modal>

      <ConfirmDialog
        open={Boolean(removing)}
        title="Delete this review?"
        confirmLabel="Delete review"
        danger
        busy={busy}
        onConfirm={remove}
        onCancel={() => setRemoving(null)}
      >
        The review is deleted permanently and {removing?.productName}&apos;s average rating is recalculated. Use this for inappropriate reviews.
      </ConfirmDialog>
    </>
  )
}
