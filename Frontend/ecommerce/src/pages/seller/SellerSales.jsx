import { useCallback, useEffect, useState } from 'react'
import sellerService from '../../services/sellerService'
import BarChart, { RankedBars } from '../../components/BarChart'
import { ErrorState, PageHeader, PageLoader, Segment, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatPrice, REPORT_RANGES, shortDate } from '../../utils/format'

export default function SellerSales() {
  const [days, setDays] = useState(30)
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    setError(null)
    setReport(null)
    sellerService
      .getSalesReport(days)
      .then(setReport)
      .catch((err) => setError(getErrorMessage(err)))
  }, [days])

  useEffect(() => {
    load()
  }, [load])

  const statusEntries = report ? Object.entries(report.itemStatusCounts).filter(([, n]) => n > 0) : []
  const ordersInRange = report ? report.dailySales.reduce((sum, d) => sum + d.count, 0) : 0

  return (
    <>
      <PageHeader
        eyebrow="Reports"
        title="Sales"
        subtitle="Paid and cash-on-delivery orders for your products. Cancelled and refunded items are excluded from sales."
        actions={<Segment label="Date range" options={REPORT_RANGES} value={days} onChange={setDays} />}
      />
      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : !report ? (
        <PageLoader />
      ) : (
        <>
          <div className="row g-3 mb-4">
            <div className="col-sm-4">
              <StatCard dark label={`Sales · last ${report.days} days`} value={formatPrice(report.grossSales)} icon="bi-currency-rupee" />
            </div>
            <div className="col-sm-4">
              <StatCard label="Units sold" value={report.unitsSold} icon="bi-box-seam" />
            </div>
            <div className="col-sm-4">
              <StatCard label="Order lines" value={ordersInRange} icon="bi-receipt" />
            </div>
          </div>

          <div className="ss-card ss-card-pad mb-4">
            <h2 className="h5 mb-3">Daily sales</h2>
            <BarChart
              title={`Daily sales for the last ${report.days} days`}
              data={report.dailySales.map((d) => ({ label: shortDate(d.date), value: Number(d.amount) }))}
              format={formatPrice}
            />
          </div>

          <div className="row g-4">
            <div className="col-lg-7">
              <div className="ss-card ss-card-pad h-100">
                <h2 className="h5 mb-3">Top products</h2>
                {report.topProducts.length === 0 ? (
                  <p className="text-muted mb-0">No sales in this period yet.</p>
                ) : (
                  <RankedBars rows={report.topProducts.map((r) => ({ label: `${r.label} (${r.units} sold)`, value: Number(r.revenue) }))} format={formatPrice} />
                )}
              </div>
            </div>
            <div className="col-lg-5">
              <div className="ss-card ss-card-pad h-100">
                <h2 className="h5 mb-3">Order lines by status</h2>
                {statusEntries.length === 0 ? (
                  <p className="text-muted mb-0">No orders yet.</p>
                ) : (
                  <ul className="list-unstyled d-grid gap-2 mb-0">
                    {statusEntries.map(([status, count]) => (
                      <li key={status} className="d-flex justify-content-between align-items-center">
                        <StatusBadge status={status} />
                        <strong>{count}</strong>
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
