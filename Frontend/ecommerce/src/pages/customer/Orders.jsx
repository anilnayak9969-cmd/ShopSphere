import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import orderService from '../../services/orderService'
import { useLiveEvent } from '../../context/LiveContext'
import Pagination from '../../components/Pagination'
import { EmptyState, ErrorState, PageLoader, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate, formatPrice } from '../../utils/format'

export default function Orders() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    setError(null)
    setResult(null)
    orderService
      .getOrders(page, 10)
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
  }, [page])

  useEffect(() => {
    load()
  }, [load])

  // Status changes arrive live; refresh the list quietly.
  useLiveEvent((type, data) => {
    if (type === 'notification' && data?.link?.startsWith('/account/orders')) {
      orderService.getOrders(page, 10).then(setResult).catch(() => {})
    }
  })

  return (
    <div>
      <span className="ss-eyebrow">My account</span>
      <h1 className="mt-1 mb-4" style={{ fontSize: 'clamp(2.2rem, 4vw, 3rem)' }}>
        My orders
      </h1>

      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : !result ? (
        <PageLoader label="Loading your orders…" />
      ) : result.content.length === 0 ? (
        <EmptyState
          icon="bi-receipt"
          title="No orders yet"
          action={
            <Link to="/products" className="btn btn-ink">
              Start shopping
            </Link>
          }
        >
          When you place an order it will appear here with its live status.
        </EmptyState>
      ) : (
        <>
          <div className="ss-card overflow-hidden">
            {result.content.map((order) => (
              <Link key={order.orderId} to={`/account/orders/${order.orderId}`} className="ss-order-row">
                <div>
                  <div className="fw-bold">{order.orderNumber}</div>
                  <div className="small text-muted">Placed {formatDate(order.createdAt)}</div>
                </div>
                <StatusBadge status={order.status} />
                <div className="text-end">
                  <div className="fw-bold">{formatPrice(order.totalAmount)}</div>
                  <div className="small text-muted">
                    View details <i className="bi bi-chevron-right" />
                  </div>
                </div>
              </Link>
            ))}
          </div>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  )
}
