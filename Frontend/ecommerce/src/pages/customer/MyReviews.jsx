import { useState } from 'react'
import { Link } from 'react-router-dom'
import accountService from '../../services/accountService'
import usePagedList from '../../hooks/usePagedList'
import { useToast } from '../../context/ToastContext'
import { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Stars } from '../../components/Rating'
import { EmptyState, ErrorState, PageLoader, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate } from '../../utils/format'

const STATUS_LABEL = { PENDING: 'Awaiting approval', APPROVED: 'Published', REJECTED: 'Not published' }

/** The customer's own reviews with their moderation status; edit on the product page or delete here. */
export default function MyReviews() {
  const toast = useToast()
  const { result, error, reload, setPage } = usePagedList((page) => accountService.getMyReviews(page, 10))
  const [removing, setRemoving] = useState(null)
  const [busy, setBusy] = useState(false)

  const remove = async () => {
    setBusy(true)
    try {
      await accountService.deleteReview(removing.productId)
      toast.info(`Your review of ${removing.productName} was deleted.`)
      setRemoving(null)
      reload()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <span className="ss-eyebrow">My account</span>
      <h1 className="h3 mt-1 mb-1">My reviews</h1>
      <p className="text-muted small mb-3">Reviews are checked by a moderator before they appear on the product page.</p>
      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !result ? (
        <PageLoader />
      ) : result.content.length === 0 ? (
        <div className="ss-card">
          <EmptyState icon="bi-star" title="No reviews yet" action={<Link to="/account/orders" className="btn btn-ink">My orders</Link>}>
            After an item is delivered, you can review it from its product page.
          </EmptyState>
        </div>
      ) : (
        <>
          <div className="ss-card px-4">
            {result.content.map((r) => (
              <article key={r.reviewId} className="ss-review">
                <div className="d-flex justify-content-between gap-2 flex-wrap align-items-center">
                  <Link to={`/products/${r.productId}#reviews`} className="fw-semibold">
                    {r.productName}
                  </Link>
                  <span className="d-flex align-items-center gap-2">
                    <StatusBadge status={r.status} label={STATUS_LABEL[r.status]} />
                    <span className="small text-muted">{formatDate(r.updatedAt || r.createdAt)}</span>
                  </span>
                </div>
                <Stars value={r.rating} showValue={false} />
                {r.title && <h2 className="h6 mt-2 mb-1">{r.title}</h2>}
                {r.comment && <p className="mb-1 small" style={{ whiteSpace: 'pre-line' }}>{r.comment}</p>}
                {r.status === 'REJECTED' && r.rejectionReason && (
                  <p className="small text-danger mb-1">
                    <i className="bi bi-info-circle me-1" aria-hidden="true" />
                    Reason: {r.rejectionReason}. Edit the review to submit it again.
                  </p>
                )}
                <div className="d-flex gap-3 small">
                  <Link to={`/products/${r.productId}#reviews`}>Edit review</Link>
                  <button type="button" className="btn btn-link btn-sm p-0 text-danger" onClick={() => setRemoving(r)}>
                    Delete
                  </button>
                </div>
              </article>
            ))}
          </div>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}

      <ConfirmDialog
        open={Boolean(removing)}
        title="Delete your review?"
        confirmLabel="Delete"
        danger
        busy={busy}
        onConfirm={remove}
        onCancel={() => setRemoving(null)}
      >
        Your review of {removing?.productName} is deleted and removed from its rating.
      </ConfirmDialog>
    </>
  )
}
