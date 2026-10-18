import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import { Alert, ErrorState, PageHeader, PageLoader, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate, formatPrice } from '../../utils/format'

export default function AdminDashboard() {
  const toast = useToast()
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [busyId, setBusyId] = useState(null)

  const load = useCallback(() => {
    setError(null)
    adminService
      .getDashboard()
      .then(setData)
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const decideSeller = async (seller, status) => {
    setBusyId(seller.id)
    try {
      await adminService.setSellerStatus(seller.id, status)
      toast.success(`${seller.storeName} ${status === 'APPROVED' ? 'approved' : 'rejected'}.`)
      load()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!data) return <PageLoader />

  const needsAttention = [
    data.pendingSellers > 0 && { to: '/admin/sellers?status=PENDING', text: `${data.pendingSellers} seller application(s)` },
    data.pendingProducts > 0 && { to: '/admin/products', text: `${data.pendingProducts} product(s) awaiting approval` },
    data.openReturns > 0 && { to: '/admin/returns', text: `${data.openReturns} open return request(s)` },
    data.unreadMessages > 0 && { to: '/admin/messages', text: `${data.unreadMessages} unresolved message(s)` },
  ].filter(Boolean)

  return (
    <>
      <PageHeader eyebrow="Admin console" title="Marketplace overview" subtitle="Every figure is calculated live from the database. Select a card to open the related page." />

      {needsAttention.length > 0 && (
        <Alert variant="warning" icon="bi-bell" className="mb-4">
          <strong>Needs attention:</strong>{' '}
          {needsAttention.map((item, i) => (
            <span key={item.to}>
              {i > 0 && ' · '}
              <Link to={item.to}>{item.text}</Link>
            </span>
          ))}
        </Alert>
      )}

      <div className="row g-3 mb-3">
        <div className="col-sm-6 col-xl-3">
          <StatCard dark to="/admin/reports" label="Revenue" value={formatPrice(data.revenue)} icon="bi-currency-rupee" foot={`${formatPrice(data.refunded)} refunded`} />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard to="/admin/orders" label="Paid orders" value={data.paidOrders} icon="bi-receipt" foot={`${data.ordersToFulfil} being fulfilled`} />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard to="/admin/orders?status=DELIVERED" label="Delivered" value={data.deliveredOrders} icon="bi-truck" foot={`${data.cancelledOrders} cancelled`} />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard to="/admin/returns" label="Open returns" value={data.openReturns} icon="bi-arrow-counterclockwise" foot="Awaiting a decision" />
        </div>
      </div>
      <div className="row g-3 mb-4">
        <div className="col-6 col-xl-3">
          <StatCard to="/admin/users" label="Customers" value={data.totalCustomers} icon="bi-people" />
        </div>
        <div className="col-6 col-xl-3">
          <StatCard to="/admin/sellers" label="Sellers" value={data.totalSellers} icon="bi-shop" foot={`${data.pendingSellers} pending approval`} />
        </div>
        <div className="col-6 col-xl-3">
          <StatCard to="/admin/products?approval=ALL" label="Products" value={data.totalProducts} icon="bi-box-seam" foot={`${data.activeProducts} active · ${data.lowStockProducts} low stock`} />
        </div>
        <div className="col-6 col-xl-3">
          <StatCard to="/admin/categories" label="Categories" value={data.totalCategories} icon="bi-tags" foot={`${data.activeCategories} active`} />
        </div>
      </div>

      <div className="row g-4">
        <div className="col-xl-8">
          <div className="ss-card overflow-hidden h-100">
            <div className="d-flex justify-content-between align-items-center p-3 px-4 border-bottom">
              <h2 className="h5 m-0">Recent orders</h2>
              <Link to="/admin/orders" className="ss-link-arrow">
                All orders <i className="bi bi-arrow-right" />
              </Link>
            </div>
            {data.recentOrders.length === 0 ? (
              <p className="text-muted p-4 m-0">No orders yet.</p>
            ) : (
              <div className="table-responsive">
                <table className="table ss-table">
                  <thead>
                    <tr>
                      <th>Order</th>
                      <th>Customer</th>
                      <th>Total</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentOrders.map((o) => (
                      <tr key={o.orderId}>
                        <td>
                          <div className="ss-cell-title">{o.orderNumber}</div>
                          <div className="ss-cell-sub">{formatDate(o.createdAt)}</div>
                        </td>
                        <td>
                          <div className="small">{o.customerName}</div>
                          <div className="ss-cell-sub">{o.paymentMethod === 'COD' ? 'Cash on delivery' : 'Razorpay'}</div>
                        </td>
                        <td className="fw-semibold">{formatPrice(o.totalAmount)}</td>
                        <td>
                          <StatusBadge status={o.status} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        <div className="col-xl-4">
          <div className="ss-card h-100">
            <div className="d-flex justify-content-between align-items-center p-3 px-4 border-bottom">
              <h2 className="h5 m-0">Seller applications</h2>
              <Link to="/admin/sellers?status=PENDING" className="ss-link-arrow">
                All <i className="bi bi-arrow-right" />
              </Link>
            </div>
            {data.recentSellerApplications.length === 0 ? (
              <p className="text-muted p-4 m-0">No applications waiting for review.</p>
            ) : (
              <ul className="list-unstyled m-0">
                {data.recentSellerApplications.map((s) => (
                  <li key={s.id} className="p-3 px-4 border-bottom">
                    <div className="fw-semibold">{s.storeName}</div>
                    <div className="small text-muted">
                      {s.fullName} · applied {formatDate(s.createdAt)}
                    </div>
                    {!s.emailVerified && <div className="small text-warning">Email not verified yet</div>}
                    <div className="d-flex gap-2 mt-2">
                      <button type="button" className="btn btn-sm btn-ink" disabled={busyId === s.id} onClick={() => decideSeller(s, 'APPROVED')}>
                        Approve
                      </button>
                      <button type="button" className="btn btn-sm btn-danger-soft" disabled={busyId === s.id} onClick={() => decideSeller(s, 'REJECTED')}>
                        Reject
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </>
  )
}
