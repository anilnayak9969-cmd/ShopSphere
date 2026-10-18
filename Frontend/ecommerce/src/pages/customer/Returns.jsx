import { Link } from 'react-router-dom'
import orderService from '../../services/orderService'
import usePagedList from '../../hooks/usePagedList'
import Pagination from '../../components/Pagination'
import { EmptyState, ErrorState, PageLoader, SafeImage, StatusBadge } from '../../components/ui'
import { formatDate, formatPrice, labelize } from '../../utils/format'

export default function Returns() {
  const { result, error, reload, setPage } = usePagedList((page) => orderService.getReturns(page, 10))

  return (
    <>
      <span className="ss-eyebrow">My account</span>
      <h1 className="h3 mt-1 mb-3">Returns &amp; refunds</h1>
      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !result ? (
        <PageLoader />
      ) : result.content.length === 0 ? (
        <div className="ss-card">
          <EmptyState icon="bi-arrow-counterclockwise" title="No returns" action={<Link to="/account/orders" className="btn btn-ink">View my orders</Link>}>
            To return a delivered item, open the order and choose “Return item”.
          </EmptyState>
        </div>
      ) : (
        <>
          <div className="ss-card overflow-hidden">
            {result.content.map((r) => (
              <div key={r.returnId} className="ss-order-row">
                <div className="d-flex gap-3 align-items-center min-w-0">
                  <div className="ss-thumb">
                    <SafeImage src={r.imageUrl} alt="" />
                  </div>
                  <div className="min-w-0">
                    <div className="fw-semibold text-truncate">{r.productName}</div>
                    <div className="small text-muted">
                      <Link to={`/account/orders/${r.orderId}`}>{r.orderNumber}</Link> · requested {formatDate(r.createdAt)} ·{' '}
                      {labelize(r.reason)}
                    </div>
                    {r.resolutionNote && <div className="small text-muted">{r.resolutionNote}</div>}
                  </div>
                </div>
                <div className="text-end">
                  <StatusBadge status={r.status === 'REQUESTED' ? 'PENDING' : r.status} label={labelize(r.status)} />
                  {r.refundAmount != null && <div className="small text-success fw-semibold mt-1">Refund {formatPrice(r.refundAmount)}</div>}
                </div>
              </div>
            ))}
          </div>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </>
  )
}
