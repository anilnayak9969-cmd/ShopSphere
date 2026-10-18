import { useState } from 'react'
import adminService from '../../services/adminService'
import usePagedList from '../../hooks/usePagedList'
import Pagination from '../../components/Pagination'
import { EmptyState, ErrorState, PageHeader, PageLoader, Segment, StatusBadge } from '../../components/ui'
import { formatDateTime, formatPrice } from '../../utils/format'

const FILTERS = [
  { value: '', label: 'All' },
  { value: 'SUCCESS', label: 'Successful' },
  { value: 'PENDING', label: 'COD due' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'REFUNDS', label: 'With refunds' },
  { value: 'CREATED', label: 'Abandoned' },
]

/** Payment monitoring: every Razorpay and cash-on-delivery payment, with refunds. Read-only. */
export default function AdminPayments() {
  const [filter, setFilter] = useState('')
  const { result, error, reload, setPage } = usePagedList(
    (page) =>
      adminService.getPayments(
        filter === 'REFUNDS' ? { refundedOnly: true, page, size: 15 } : { status: filter, page, size: 15 },
      ),
    [filter],
  )

  return (
    <>
      <PageHeader
        eyebrow="Finance"
        title="Payments & refunds"
        subtitle="Online payments are verified with Razorpay's signature before an order is confirmed. Refunds are issued automatically on cancellations and approved returns."
      />
      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <Segment label="Payment filter" options={FILTERS} value={filter} onChange={setFilter} />
        </div>
        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-credit-card" title="No payments">
            Nothing matches this filter.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Method</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Razorpay reference</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((p) => (
                  <tr key={p.paymentId}>
                    <td>
                      <div className="ss-cell-title">{p.orderNumber}</div>
                      <div className="ss-cell-sub">{formatDateTime(p.createdAt)}</div>
                    </td>
                    <td>
                      <div className="small">{p.customerName}</div>
                      <div className="ss-cell-sub">{p.customerEmail}</div>
                    </td>
                    <td className="small">{p.method === 'COD' ? 'Cash on delivery' : 'Razorpay'}</td>
                    <td>
                      <div className="fw-semibold">{formatPrice(p.amount)}</div>
                      {Number(p.refundedAmount) > 0 && <div className="small text-success">Refunded {formatPrice(p.refundedAmount)}</div>}
                    </td>
                    <td>
                      <StatusBadge status={p.status} label={p.status === 'PENDING' && p.method === 'COD' ? 'Collect on delivery' : undefined} />
                      {p.failureReason && p.status !== 'SUCCESS' && (
                        <div className="ss-cell-sub mt-1" style={{ maxWidth: 220 }}>
                          {p.failureReason}
                        </div>
                      )}
                    </td>
                    <td className="small text-muted">
                      {p.razorpayPaymentId || '—'}
                      {p.razorpayOrderId && <div className="ss-cell-sub">{p.razorpayOrderId}</div>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}
    </>
  )
}
