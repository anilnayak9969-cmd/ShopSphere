import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import shippingService from '../services/shippingService'
import { Alert, SafeImage, StatusBadge } from '../components/ui'
import { getErrorMessage } from '../utils/errors'
import { formatDate, formatDateTime, labelize } from '../utils/format'

/** Shipping Management: a customer tracks a shipment with the tracking number from the courier. */
const STEPS = [
  { key: 'PENDING', label: 'Order placed', icon: 'bi-receipt' },
  { key: 'SHIPPED', label: 'Shipped', icon: 'bi-box-seam' },
  { key: 'IN_TRANSIT', label: 'In transit', icon: 'bi-truck' },
  { key: 'OUT_FOR_DELIVERY', label: 'Out for delivery', icon: 'bi-geo-alt' },
  { key: 'DELIVERED', label: 'Delivered', icon: 'bi-house-check' },
]

export default function TrackShipment() {
  const [params, setParams] = useSearchParams()
  const [number, setNumber] = useState(params.get('number') || '')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const lookup = async (value) => {
    const n = value.trim()
    if (n.length < 3) {
      setError('Enter the tracking number from your order or the courier.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      setResult(await shippingService.track(n))
      setParams({ number: n }, { replace: true })
    } catch (err) {
      setResult(null)
      setError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    const initial = params.get('number')
    if (initial) lookup(initial)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const reached = result ? STEPS.findIndex((s) => s.key === result.shippingStatus) : -1
  const stopped = result && ['CANCELLED', 'RETURNED'].includes(result.shippingStatus)

  return (
    <div className="container ss-page" style={{ maxWidth: 960 }}>
      <span className="ss-eyebrow">Shipping</span>
      <h1 className="h3 mt-1">Track your shipment</h1>
      <p className="text-muted">Enter the tracking number shown on your order (for example SSD-15) or sent by the courier.</p>

      <form
        className="ss-card ss-card-pad d-flex gap-2 flex-wrap mb-4"
        onSubmit={(e) => {
          e.preventDefault()
          lookup(number)
        }}
      >
        <label htmlFor="tracking-number" className="visually-hidden">
          Tracking number
        </label>
        <input
          id="tracking-number"
          className="form-control form-control-lg flex-grow-1"
          style={{ minWidth: 220, maxWidth: 520 }}
          placeholder="Tracking number"
          value={number}
          onChange={(e) => setNumber(e.target.value)}
        />
        <button type="submit" className="btn btn-ink btn-lg" disabled={busy}>
          {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
          Track
        </button>
      </form>

      {error && <Alert variant="danger">{error}</Alert>}

      {result && (
        <>
          <div className="ss-card ss-card-pad mb-3">
            <div className="d-flex justify-content-between flex-wrap gap-3 mb-3">
              <div>
                <div className="small text-muted">Tracking number</div>
                <div className="h5 mb-0">{result.trackingNumber}</div>
                <div className="small text-muted">{result.courierService || 'Courier not assigned yet'}</div>
              </div>
              <div>
                <div className="small text-muted">Order</div>
                <Link to={`/account/orders/${result.orderId}`} className="fw-semibold">
                  {result.orderNumber}
                </Link>
                <div className="small text-muted">
                  {result.shippingMethod === 'EXPRESS' ? 'Express' : 'Standard'} delivery to {result.city} {result.pincode}
                </div>
              </div>
              <div className="text-md-end">
                <div className="small text-muted">{result.deliveredAt ? 'Delivered on' : 'Estimated delivery'}</div>
                <div className="h5 mb-0">{result.deliveredAt ? formatDate(result.deliveredAt) : result.estimatedDelivery ? formatDate(result.estimatedDelivery) : '—'}</div>
                <StatusBadge status={result.shippingStatus} label={labelize(result.shippingStatus)} />
              </div>
            </div>
            {stopped ? (
              <Alert variant="warning" className="mb-0">
                This shipment was {result.shippingStatus === 'CANCELLED' ? 'cancelled' : 'returned'}.
              </Alert>
            ) : (
              <div className="d-flex justify-content-between text-center gap-1" aria-label="Shipment progress">
                {STEPS.map((s, i) => (
                  <div key={s.key} className="flex-fill">
                    <i className={`bi ${s.icon} fs-4 ${i <= reached ? 'text-success' : 'text-muted opacity-50'}`} aria-hidden="true" />
                    <div className={`small ${i <= reached ? 'fw-semibold' : 'text-muted'}`}>{s.label}</div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="row g-3">
            <div className="col-md-5">
              <div className="ss-card ss-card-pad h-100">
                <h2 className="h6">In this shipment</h2>
                {result.items.map((i, idx) => (
                  <div key={idx} className="d-flex gap-2 align-items-center py-2 border-bottom">
                    <div className="ss-thumb" style={{ width: 44, height: 44 }}>
                      <SafeImage src={i.imageUrl} alt="" />
                    </div>
                    <div className="small">
                      <div className="fw-semibold">
                        {i.productName} × {i.quantity}
                      </div>
                      <div className="text-muted">{labelize(i.itemStatus)}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <div className="col-md-7">
              <div className="ss-card ss-card-pad h-100">
                <h2 className="h6">Tracking history</h2>
                <ul className="list-unstyled mb-0">
                  {[...result.history].reverse().map((h, idx) => (
                    <li key={idx} className="d-flex gap-2 py-2 border-bottom small">
                      <i className={`bi bi-circle-fill ${idx === 0 ? 'text-primary' : 'text-muted'}`} style={{ fontSize: 8, marginTop: 6 }} aria-hidden="true" />
                      <div>
                        <div className="fw-semibold">{h.description}</div>
                        <div className="text-muted">
                          {formatDateTime(h.at)}
                          {h.productName && ` · ${h.productName}`}
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
