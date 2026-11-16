import { useCallback, useEffect, useState } from 'react'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import Modal from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { EmptyState, ErrorState, PageHeader, PageLoader, SafeImage, Segment, StatCard } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDateTime, formatPrice } from '../../utils/format'

/** Cart Management module — customer carts and cart-abandonment analysis for admins. */
const STATES = [
  { value: '', label: 'All carts' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ABANDONED', label: 'Abandoned' },
  { value: 'EMPTY', label: 'Empty' },
]
const HOURS = [1, 6, 24, 72]
const PILL = { ACTIVE: 'ss-pill-success', ABANDONED: 'ss-pill-warning', EMPTY: 'ss-pill-muted' }

const idle = (h) => (h < 1 ? 'just now' : h < 48 ? `${h} h ago` : `${Math.floor(h / 24)} days ago`)

export default function AdminCarts() {
  const toast = useToast()
  const [state, setState] = useState('')
  const [hours, setHours] = useState(24)
  const [search, setSearch] = useState('')
  const debounced = useDebounce(search.trim(), 300)
  const [summary, setSummary] = useState(null)
  const [detail, setDetail] = useState(null)
  const [busy, setBusy] = useState(false)
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => adminService.getCarts({ state, search: debounced, abandonedAfterHours: hours, page, size: 10 }),
    [state, debounced, hours],
  )

  const loadSummary = useCallback(() => {
    adminService.getCartSummary(hours).then(setSummary).catch(() => setSummary(null))
  }, [hours])
  useEffect(() => {
    loadSummary()
  }, [loadSummary])

  const open = async (cart) => {
    try {
      setDetail(await adminService.getCart(cart.cartId, hours))
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  const remind = async (cart) => {
    setBusy(true)
    try {
      const updated = await adminService.remindCart(cart.cartId, hours)
      setResult((r) => ({ ...r, content: r.content.map((c) => (c.cartId === updated.cartId ? updated : c)) }))
      if (detail?.summary.cartId === updated.cartId) setDetail((d) => ({ ...d, summary: updated }))
      toast.success(`Reminder sent to ${updated.customerName}.`)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const canRemind = (c) => c.state === 'ABANDONED' && c.customerActive && !(c.reminderSentAt && c.reminderSentAt > c.updatedAt)

  return (
    <>
      <PageHeader
        eyebrow="Sales"
        title="Cart management"
        subtitle="What customers have in their carts right now, which carts were abandoned, and which products get left behind."
      />

      <div className="row g-3 mb-4">
        <div className="col-6 col-lg-3">
          <StatCard label="Active carts" value={summary?.activeCarts ?? '—'} icon="bi-cart-check" foot={summary && `${formatPrice(summary.activeValue)} in carts now`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Abandoned carts" value={summary?.abandonedCarts ?? '—'} icon="bi-cart-x" dark foot={summary && `${formatPrice(summary.abandonedValue)} left behind`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Abandonment rate" value={summary ? `${summary.abandonmentRate}%` : '—'} icon="bi-graph-down-arrow" foot={summary && `vs ${summary.ordersLast30Days} orders in 30 days`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Avg abandoned cart" value={summary ? formatPrice(summary.averageAbandonedValue) : '—'} icon="bi-receipt" foot={summary && `${summary.totalCarts} carts · ${summary.emptyCarts} empty`} />
        </div>
      </div>

      {summary?.topAbandonedProducts?.length > 0 && (
        <div className="ss-card ss-card-pad mb-4">
          <h2 className="h6 mb-3">Most abandoned products</h2>
          <div className="table-responsive">
            <table className="table table-sm small mb-0">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="text-end">Carts</th>
                  <th className="text-end">Units</th>
                  <th className="text-end">Value</th>
                </tr>
              </thead>
              <tbody>
                {summary.topAbandonedProducts.map((p) => (
                  <tr key={p.productId}>
                    <td>{p.productName}</td>
                    <td className="text-end">{p.carts}</td>
                    <td className="text-end">{p.units}</td>
                    <td className="text-end">{formatPrice(p.value)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar flex-wrap">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input type="search" className="form-control form-control-sm" placeholder="Customer name or email" aria-label="Search carts" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <Segment label="Cart state" options={STATES} value={state} onChange={setState} />
          <select className="form-select form-select-sm ms-auto" style={{ maxWidth: 230 }} aria-label="Abandoned after" value={hours} onChange={(e) => setHours(Number(e.target.value))}>
            {HOURS.map((h) => (
              <option key={h} value={h}>
                Abandoned after {h < 24 ? `${h} h` : `${h / 24} day${h > 24 ? 's' : ''}`} idle
              </option>
            ))}
          </select>
        </div>
        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-cart" title="No carts found">
            No carts match these filters.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Products</th>
                  <th>Items</th>
                  <th>Total</th>
                  <th>Last activity</th>
                  <th>State</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((c) => (
                  <tr key={c.cartId}>
                    <td>
                      <div className="ss-cell-title">{c.customerName}</div>
                      <div className="ss-cell-sub">{c.customerEmail}</div>
                    </td>
                    <td className="small" style={{ maxWidth: 260 }}>
                      {c.productNames.length ? c.productNames.join(', ') : '—'}
                      {c.lines > c.productNames.length && ` +${c.lines - c.productNames.length} more`}
                      {c.unavailableLines > 0 && <div className="text-danger">{c.unavailableLines} unavailable</div>}
                    </td>
                    <td>{c.itemCount}</td>
                    <td className="fw-semibold text-nowrap">{formatPrice(c.total)}</td>
                    <td className="small text-nowrap">
                      {idle(c.idleHours)}
                      <div className="ss-cell-sub">{formatDateTime(c.updatedAt)}</div>
                    </td>
                    <td>
                      <span className={`ss-pill ${PILL[c.state]}`}>{c.state === 'ABANDONED' ? 'Abandoned' : c.state === 'ACTIVE' ? 'Active' : 'Empty'}</span>
                      {c.reminderSentAt && <div className="ss-cell-sub mt-1">Reminded {formatDateTime(c.reminderSentAt)}</div>}
                    </td>
                    <td className="text-end text-nowrap">
                      <button type="button" className="btn btn-sm btn-outline-ink me-1" onClick={() => open(c)} disabled={c.lines === 0}>
                        View cart
                      </button>
                      {canRemind(c) && (
                        <button type="button" className="btn btn-sm btn-ink" onClick={() => remind(c)} disabled={busy}>
                          <i className="bi bi-bell me-1" aria-hidden="true" />
                          Remind
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}

      <Modal
        open={Boolean(detail)}
        size="lg"
        title={detail && `${detail.summary.customerName}'s cart`}
        subtitle={detail && `${detail.summary.customerEmail} · last activity ${formatDateTime(detail.summary.updatedAt)}`}
        onClose={() => setDetail(null)}
        footer={
          detail &&
          canRemind(detail.summary) && (
            <button type="button" className="btn btn-ink" onClick={() => remind(detail.summary)} disabled={busy}>
              <i className="bi bi-bell me-1" aria-hidden="true" />
              Send reminder
            </button>
          )
        }
      >
        {detail && (
          <>
            <table className="table small align-middle">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="text-end">Price</th>
                  <th className="text-center">Qty</th>
                  <th className="text-end">Total</th>
                </tr>
              </thead>
              <tbody>
                {detail.cart.items.map((i) => (
                  <tr key={i.cartItemId}>
                    <td>
                      <div className="d-flex gap-2 align-items-center">
                        <div className="ss-thumb" style={{ width: 40, height: 40 }}>
                          <SafeImage src={i.imageUrl} alt="" />
                        </div>
                        <div>
                          <div className="fw-semibold">{i.productName}</div>
                          <div className="text-muted">
                            {i.storeName} · {i.stockQuantity} in stock
                            {!i.available && <span className="text-danger"> · {i.availabilityMessage}</span>}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="text-end">{formatPrice(i.unitPrice)}</td>
                    <td className="text-center">{i.quantity}</td>
                    <td className="text-end fw-semibold">{formatPrice(i.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="ss-summary-row">
              <span>Items ({detail.cart.itemCount})</span>
              <span>{formatPrice(detail.cart.subtotal)}</span>
            </div>
            <div className="ss-summary-row">
              <span>Delivery</span>
              <span>{Number(detail.cart.shippingFee) ? formatPrice(detail.cart.shippingFee) : 'Free'}</span>
            </div>
            <div className="ss-summary-row ss-summary-total">
              <span>Total</span>
              <span>{formatPrice(detail.cart.total)}</span>
            </div>
          </>
        )}
      </Modal>
    </>
  )
}
