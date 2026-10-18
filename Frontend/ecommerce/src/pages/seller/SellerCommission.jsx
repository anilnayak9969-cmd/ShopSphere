import { useCallback, useEffect, useState } from 'react'
import sellerService from '../../services/sellerService'
import { Alert, EmptyState, ErrorState, PageHeader, PageLoader, StatCard } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatPrice } from '../../utils/format'

const monthName = (year, month) => new Date(year, month - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })

export default function SellerCommission() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    setError(null)
    sellerService
      .getCommission()
      .then(setData)
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!data) return <PageLoader />

  return (
    <>
      <PageHeader eyebrow="Reports" title="Commission & earnings" subtitle="Commission is charged only on delivered items that were not returned." />
      <Alert variant="info" className="mb-4">
        The current platform commission is <strong>{Number(data.commissionPercent)}%</strong> of the item price. It is set by the
        ShopSphere admin team.
      </Alert>

      <div className="row g-3 mb-4">
        <div className="col-sm-6 col-xl-3">
          <StatCard label="Delivered sales" value={formatPrice(data.deliveredSales)} icon="bi-truck" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard label="Commission" value={formatPrice(data.totalCommission)} icon="bi-percent" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard dark label="Your earnings" value={formatPrice(data.netEarnings)} icon="bi-wallet2" foot="Delivered sales minus commission" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard label="Not yet delivered" value={formatPrice(data.pendingSettlement)} icon="bi-hourglass-split" foot="Becomes earnings on delivery" />
        </div>
      </div>

      <div className="ss-card overflow-hidden">
        <div className="p-3 px-4 border-bottom">
          <h2 className="h5 m-0">Monthly statement</h2>
        </div>
        {data.months.length === 0 ? (
          <EmptyState icon="bi-calendar3" title="No delivered sales yet">
            Each month with delivered items will be listed here.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Month</th>
                  <th>Units</th>
                  <th>Gross sales</th>
                  <th>Commission</th>
                  <th>Net earnings</th>
                </tr>
              </thead>
              <tbody>
                {data.months.map((m) => (
                  <tr key={`${m.year}-${m.month}`}>
                    <td className="fw-semibold">{monthName(m.year, m.month)}</td>
                    <td>{m.units}</td>
                    <td>{formatPrice(m.grossSales)}</td>
                    <td className="text-danger">− {formatPrice(m.commission)}</td>
                    <td className="fw-semibold">{formatPrice(m.netEarnings)}</td>
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
