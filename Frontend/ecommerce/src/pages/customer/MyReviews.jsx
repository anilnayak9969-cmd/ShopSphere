import { Link } from 'react-router-dom'
import accountService from '../../services/accountService'
import usePagedList from '../../hooks/usePagedList'
import Pagination from '../../components/Pagination'
import { Stars } from '../../components/Rating'
import { EmptyState, ErrorState, PageLoader } from '../../components/ui'
import { formatDate } from '../../utils/format'

export default function MyReviews() {
  const { result, error, reload, setPage } = usePagedList((page) => accountService.getMyReviews(page, 10))

  return (
    <>
      <span className="ss-eyebrow">My account</span>
      <h1 className="h3 mt-1 mb-3">My reviews</h1>
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
                <div className="d-flex justify-content-between gap-2 flex-wrap">
                  <Link to={`/products/${r.productId}#reviews`} className="fw-semibold">
                    {r.productName}
                  </Link>
                  <span className="small text-muted">{formatDate(r.updatedAt || r.createdAt)}</span>
                </div>
                <Stars value={r.rating} showValue={false} />
                {r.title && <h2 className="h6 mt-2 mb-1">{r.title}</h2>}
                {r.comment && <p className="mb-0 small">{r.comment}</p>}
                <Link to={`/products/${r.productId}#reviews`} className="small">
                  Edit review
                </Link>
              </article>
            ))}
          </div>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </>
  )
}
