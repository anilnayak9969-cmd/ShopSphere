import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import sellerService from '../../services/sellerService'
import { useAuth } from '../../context/AuthContext'
import { useLiveEvent } from '../../context/LiveContext'
import { ErrorState, PageHeader, PageLoader, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate, formatPrice } from '../../utils/format'
import SellerStatusBanner from './SellerStatusBanner'

export default function SellerDashboard() {
  const { user, refreshUser } = useAuth()
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    setError(null)
    sellerService
      .getDashboard()
      .then(setData)
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  useLiveEvent((type) => {
    if (type === 'notification') sellerService.getDashboard().then(setData).catch(() => {})
  })

  // If an admin approved the store since this session started, refresh the cached user.
  useEffect(() => {
    if (data && user && data.sellerStatus !== user.sellerStatus) refreshUser().catch(() => {})
  }, [data, user, refreshUser])

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!data) return <PageLoader />

  return (
    <>
      <PageHeader
        eyebrow={data.storeName}
        title="Studio overview"
        subtitle="Live figures from your products and paid orders. Select a card to open the related page."
        actions={
          <Link to="/seller/products/new" className={`btn btn-ink ${data.sellerStatus !== 'APPROVED' ? 'disabled' : ''}`}>
            <i className="bi bi-plus-lg me-2" />
            Add product
          </Link>
        }
      />
      <SellerStatusBanner status={data.sellerStatus} />

      <div className="row g-3 mb-3">
        <div className="col-sm-6 col-xl-3">
          <StatCard dark to="/seller/sales" label="Revenue" value={formatPrice(data.revenue)} icon="bi-currency-rupee" foot={`${data.unitsSold} units sold`} />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard to="/seller/orders" label="To fulfil" value={data.itemsToFulfil} icon="bi-box2" foot={`${data.orders} paid orders in total`} />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard to="/seller/orders?status=DELIVERED" label="Delivered" value={data.itemsDelivered} icon="bi-truck" foot="Order lines delivered" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard to="/seller/returns" label="Open returns" value={data.openReturns} icon="bi-arrow-counterclockwise" foot="Waiting for your decision" />
        </div>
      </div>
      <div className="row g-3 mb-4">
        <div className="col-sm-4">
          <StatCard to="/seller/products" label="Products" value={data.totalProducts} icon="bi-box-seam" foot={`${data.activeProducts} live in the store`} />
        </div>
        <div className="col-sm-4">
          <StatCard to="/seller/products?approval=PENDING" label="Awaiting approval" value={data.pendingApprovalProducts} icon="bi-hourglass-split" foot="Reviewed by the ShopSphere team" />
        </div>
        <div className="col-sm-4">
          <StatCard to="/seller/inventory" label="Low stock" value={data.lowStockProducts} icon="bi-exclamation-triangle" foot={`${data.lowStockThreshold} units or fewer`} />
        </div>
      </div>

      <div className="ss-card overflow-hidden">
        <div className="d-flex justify-content-between align-items-center p-3 px-4 border-bottom">
          <h2 className="h4 m-0">Recent orders</h2>
          <Link to="/seller/orders" className="ss-link-arrow">
            View all <i className="bi bi-arrow-right" />
          </Link>
        </div>
        {data.recentOrderItems.length === 0 ? (
          <p className="text-muted p-4 m-0">No orders yet. They will appear here as soon as customers buy your products.</p>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Product</th>
                  <th>Qty</th>
                  <th>Amount</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {data.recentOrderItems.map((item) => (
                  <tr key={item.orderItemId}>
                    <td>
                      <div className="ss-cell-title">{item.orderNumber}</div>
                      <div className="ss-cell-sub">{formatDate(item.orderedAt)}</div>
                    </td>
                    <td>{item.productName}</td>
                    <td>{item.quantity}</td>
                    <td>{formatPrice(item.lineTotal)}</td>
                    <td>
                      <StatusBadge status={item.itemStatus} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}
