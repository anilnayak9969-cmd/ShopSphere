import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Stars } from '../../components/Rating'
import { EmptyState, ErrorState, PageHeader, PageLoader } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate } from '../../utils/format'

const FILTERS = ['', '5', '4', '3', '2', '1']

export default function AdminReviews() {
  const toast = useToast()
  const [rating, setRating] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [target, setTarget] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setError(null)
    adminService
      .getReviews({ rating, page, size: 10 })
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
  }, [rating, page])

  useEffect(() => {
    load()
  }, [load])

  const remove = async () => {
    setBusy(true)
    try {
      await adminService.deleteReview(target.reviewId)
      toast.success('Review removed and the product rating recalculated.')
      setTarget(null)
      load()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader eyebrow="Moderation" title="Reviews" subtitle="Only verified buyers can review. Remove anything abusive or off-topic." />
      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-segment" role="group" aria-label="Filter by rating">
            {FILTERS.map((f) => (
              <button
                type="button"
                key={f || 'all'}
                className={rating === f ? 'active' : ''}
                aria-pressed={rating === f}
                onClick={() => {
                  setRating(f)
                  setPage(0)
                }}
              >
                {f ? `${f} ★` : 'All'}
              </button>
            ))}
          </div>
        </div>
        {error ? (
          <ErrorState message={error} onRetry={load} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-chat-square-quote" title="No reviews">
            Reviews appear here once customers review products they bought.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Rating</th>
                  <th>Review</th>
                  <th>By</th>
                  <th className="text-end">Action</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((r) => (
                  <tr key={r.reviewId}>
                    <td>
                      <Link to={`/products/${r.productId}`} className="ss-cell-title text-decoration-none">
                        {r.productName}
                      </Link>
                    </td>
                    <td>
                      <Stars value={r.rating} showValue={false} />
                    </td>
                    <td style={{ maxWidth: 360 }}>
                      {r.title && <div className="fw-semibold">{r.title}</div>}
                      <div className="ss-cell-sub" title={r.comment || ''}>
                        {r.comment || '—'}
                      </div>
                    </td>
                    <td className="small">
                      {r.reviewerName}
                      <div className="text-muted">{formatDate(r.createdAt)}</div>
                    </td>
                    <td className="text-end">
                      <button type="button" className="btn btn-danger-soft btn-sm" onClick={() => setTarget(r)}>
                        Remove
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

      <ConfirmDialog
        open={Boolean(target)}
        title="Remove this review?"
        confirmLabel="Remove review"
        danger
        busy={busy}
        onConfirm={remove}
        onCancel={() => setTarget(null)}
      >
        The review is deleted permanently and {target?.productName}&apos;s average rating is recalculated.
      </ConfirmDialog>
    </>
  )
}
