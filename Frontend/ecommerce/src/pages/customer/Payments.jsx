import { Link } from 'react-router-dom'
import paymentService from '../../services/paymentService'
import usePagedList from '../../hooks/usePagedList'
import Pagination from '../../components/Pagination'
import { EmptyState, ErrorState, PageLoader, StatusBadge } from '../../components/ui'
import { formatDateTime, formatPrice } from '../../utils/format'

export default function Payments() {
  const { result, error, reload, setPage } = usePagedList((page) => paymentService.getHistory(page, 10))

  return (
    <>
      <span className="ss-eyebrow">My account</span>
      <h1 className="h3 mt-1 mb-3">Payment history</h1>
      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !result ? (
        <PageLoader />
      ) : result.content.length === 0 ? (
        <div className="ss-card">
          <EmptyState icon="bi-credit-card" title="No payments yet">
            Payments for your orders will be listed here.
          </EmptyState>
        </div>
      ) : (
        <>
          <div className="ss-card overflow-hidden">
            <div className="table-responsive">
              <table className="table ss-table">
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Method</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Reference</th>
                  </tr>
                </thead>
                <tbody>
                  {result.content.map((p) => (
                    <tr key={p.paymentId}>
                      <td>
                        <Link to={`/account/orders/${p.orderId}`} className="ss-cell-title text-decoration-none">
                          {p.orderNumber}
                        </Link>
                        <div className="ss-cell-sub">{formatDateTime(p.createdAt)}</div>
                      </td>
                      <td>{p.method === 'COD' ? 'Cash on Delivery' : 'Online (Razorpay)'}</td>
                      <td>
                        <div className="fw-semibold">{formatPrice(p.amount)}</div>
                        {Number(p.refundedAmount) > 0 && <div className="small text-success">Refunded {formatPrice(p.refundedAmount)}</div>}
                      </td>
                      <td>
                        <StatusBadge status={p.status} label={p.status === 'PENDING' && p.method === 'COD' ? 'Pay on delivery' : undefined} />
                        {p.failureReason && p.status !== 'SUCCESS' && <div className="ss-cell-sub">{p.failureReason}</div>}
                      </td>
                      <td className="small text-muted">{p.razorpayPaymentId || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </>
  )
}
