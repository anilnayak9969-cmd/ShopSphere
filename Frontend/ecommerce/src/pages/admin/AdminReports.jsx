import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import adminService from '../../services/adminService'
import BarChart, { RankedBars } from '../../components/BarChart'
import { ErrorState, PageHeader, PageLoader, SafeImage, Segment, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatPrice, REPORT_RANGES, shortDate } from '../../utils/format'

export default function AdminReports() {
  const [days, setDays] = useState(30)
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    setError(null)
    setReport(null)
    adminService
      .getReports(days)
      .then(setReport)
      .catch((err) => setError(getErrorMessage(err)))
  }, [days])

  useEffect(() => {
    load()
  }, [load])

  const ordersInRange = report ? report.dailyOrders.reduce((sum, d) => sum + d.count, 0) : 0
  const statusEntries = report ? Object.entries(report.orderStatusCounts).filter(([, n]) => n > 0) : []

  return (
    <>
      <PageHeader
        eyebrow="Analytics"
        title="Reports"
        subtitle="Marketplace performance calculated from orders, payments and refunds."
        actions={<Segment label="Date range" options={REPORT_RANGES} value={days} onChange={setDays} />}
      />
      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : !report ? (
        <PageLoader />
      ) : (
        <>
          <div className="row g-3 mb-4">
            <div className="col-sm-6 col-xl-3">
              <StatCard dark label="Gross merchandise value" value={formatPrice(report.grossMerchandiseValue)} icon="bi-graph-up-arrow" foot={`Last ${report.days} days`} />
            </div>
            <div className="col-sm-6 col-xl-3">
              <StatCard label="Orders" value={ordersInRange} icon="bi-receipt" foot={`Last ${report.days} days`} />
            </div>
            <div className="col-sm-6 col-xl-3">
              <StatCard label="Refunded" value={formatPrice(report.refundedTotal)} icon="bi-arrow-counterclockwise" to="/admin/payments" />
            </div>
            <div className="col-sm-6 col-xl-3">
              <StatCard label="Estimated commission" value={formatPrice(report.estimatedCommission)} icon="bi-percent" foot="On delivered items" />
            </div>
          </div>

          <div className="ss-card ss-card-pad mb-4">
            <h2 className="h5 mb-3">Orders per day</h2>
            <BarChart
              title={`Orders per day for the last ${report.days} days`}
              data={report.dailyOrders.map((d) => ({ label: shortDate(d.date), value: d.count }))}
            />
          </div>

          <div className="row g-4 mb-4">
            <div className="col-lg-6">
              <div className="ss-card ss-card-pad h-100">
                <h2 className="h5 mb-3">Top categories</h2>
                {report.topCategories.length === 0 ? (
                  <p className="text-muted mb-0">No sales in this period.</p>
                ) : (
                  <RankedBars rows={report.topCategories.map((r) => ({ label: `${r.label} (${r.units} sold)`, value: Number(r.revenue) }))} format={formatPrice} />
                )}
              </div>
            </div>
            <div className="col-lg-6">
              <div className="ss-card ss-card-pad h-100">
                <h2 className="h5 mb-3">Top sellers</h2>
                {report.topSellers.length === 0 ? (
                  <p className="text-muted mb-0">No sales in this period.</p>
                ) : (
                  <RankedBars rows={report.topSellers.map((r) => ({ label: `${r.label} (${r.units} sold)`, value: Number(r.revenue) }))} format={formatPrice} />
                )}
              </div>
            </div>
          </div>

          <div className="row g-4">
            <div className="col-lg-5">
              <div className="ss-card ss-card-pad h-100">
                <h2 className="h5 mb-3">Orders by status</h2>
                {statusEntries.length === 0 ? (
                  <p className="text-muted mb-0">No orders yet.</p>
                ) : (
                  <ul className="list-unstyled d-grid gap-2 mb-0">
                    {statusEntries.map(([status, count]) => (
                      <li key={status} className="d-flex justify-content-between align-items-center">
                        <StatusBadge status={status} />
                        <Link to={`/admin/orders?status=${status}`} className="fw-bold">
                          {count}
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
            <div className="col-lg-7">
              <div className="ss-card ss-card-pad h-100">
                <h2 className="h5 mb-3">Low stock</h2>
                {report.lowStockProducts.length === 0 ? (
                  <p className="text-muted mb-0">Every active product is well stocked.</p>
                ) : (
                  <ul className="list-unstyled d-grid gap-2 mb-0">
                    {report.lowStockProducts.map((p) => (
                      <li key={p.productId} className="d-flex align-items-center gap-2">
                        <div className="ss-thumb">
                          <SafeImage src={p.imageUrl} alt="" />
                        </div>
                        <div className="min-w-0 flex-grow-1">
                          <div className="small fw-semibold text-truncate">{p.productName}</div>
                          <div className="ss-cell-sub">{p.storeName}</div>
                        </div>
                        <span className={`ss-pill ${p.stockQuantity === 0 ? 'ss-pill-danger' : 'ss-pill-warning'}`}>{p.stockQuantity} left</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          </div>
        </>
      )}
    </>
  )
}
