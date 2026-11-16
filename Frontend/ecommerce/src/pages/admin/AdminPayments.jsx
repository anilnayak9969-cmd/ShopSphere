import { useCallback, useEffect, useState } from 'react'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import Modal from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field } from '../../components/FormControls'
import { Alert, EmptyState, ErrorState, PageHeader, PageLoader, Segment, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDateTime, formatPrice, labelize } from '../../utils/format'

/**
 * Payment Management module — the admin Payment dashboard.
 * Transaction history (payment ID, amount, method, status), refunds for cancelled or returned
 * orders, and the refund log.
 */
const VIEWS = [
  { value: 'payments', label: 'Transactions' },
  { value: 'refunds', label: 'Refund history' },
]

const FILTERS = [
  { value: '', label: 'All' },
  { value: 'SUCCESS', label: 'Paid' },
  { value: 'PENDING', label: 'COD due' },
  { value: 'REFUNDED', label: 'Refunded' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'REFUNDS', label: 'Any refund' },
  { value: 'CREATED', label: 'Abandoned' },
]

const METHOD_ICONS = {
  CARD: 'bi-credit-card',
  UPI: 'bi-phone',
  NETBANKING: 'bi-bank',
  WALLET: 'bi-wallet2',
  PAYPAL: 'bi-paypal',
  CASH: 'bi-cash-coin',
}

const STATUS_LABELS = { SUCCESS: 'Paid', PENDING: 'Due', CREATED: 'Not completed' }

function MethodCell({ p }) {
  const label = p.instrumentLabel || (p.method === 'COD' ? 'Cash on delivery' : 'Online — not completed')
  return (
    <div className="d-flex gap-2 align-items-start">
      <i className={`bi ${METHOD_ICONS[p.instrument] || 'bi-globe'} mt-1`} aria-hidden="true" />
      <div>
        <div className="small fw-semibold">{label}</div>
        <div className="ss-cell-sub">
          {p.instrumentDetail ? `${p.instrumentDetail} · ` : ''}
          {p.method === 'COD' ? 'paid on delivery' : 'via Razorpay'}
        </div>
      </div>
    </div>
  )
}

export default function AdminPayments() {
  const toast = useToast()
  const [view, setView] = useState('payments')
  const [filter, setFilter] = useState('')
  const [method, setMethod] = useState('')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const [summary, setSummary] = useState(null)

  const payments = usePagedList(
    (page) =>
      adminService.getPayments({
        ...(filter === 'REFUNDS' ? { refundedOnly: true } : { status: filter }),
        method,
        search: debouncedSearch,
        page,
        size: 10,
      }),
    [filter, method, debouncedSearch],
  )
  const refunds = usePagedList((page) => adminService.getRefunds({ page, size: 10 }), [view])

  const [target, setTarget] = useState(null) // payment being refunded
  const [history, setHistory] = useState([])
  const [form, setForm] = useState({ amount: '', reason: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [busy, setBusy] = useState(false)

  const loadSummary = useCallback(() => {
    adminService.getPaymentSummary().then(setSummary).catch(() => setSummary(null))
  }, [])
  useEffect(() => {
    loadSummary()
  }, [loadSummary])

  const openRefund = async (p) => {
    setTarget(p)
    setForm({ amount: String(p.refundable), reason: '' })
    setErrors({})
    setFormError(null)
    setHistory([])
    adminService.getPaymentRefunds(p.paymentId).then(setHistory).catch(() => setHistory([]))
  }

  const submitRefund = async (event) => {
    event.preventDefault()
    const found = {}
    const amount = Number(form.amount)
    if (!form.amount || Number.isNaN(amount) || amount < 1) found.amount = 'Refund amount must be at least ₹1'
    else if (amount > Number(target.refundable)) found.amount = `You can refund at most ${formatPrice(target.refundable)}`
    else if (!/^\d+(\.\d{1,2})?$/.test(form.amount)) found.amount = 'Use at most 2 decimal places'
    if (form.reason.trim().length < 5) found.reason = 'Give a reason of at least 5 characters'
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return
    setBusy(true)
    try {
      const updated = await adminService.issueRefund(target.paymentId, { amount, reason: form.reason.trim() })
      payments.setResult((r) => ({ ...r, content: r.content.map((p) => (p.paymentId === updated.paymentId ? updated : p)) }))
      toast.success(`Refund of ${formatPrice(amount)} issued for order ${updated.orderNumber}.`)
      setTarget(null)
      loadSummary()
      refunds.reload()
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
      refunds.reload()
    } finally {
      setBusy(false)
    }
  }

  const list = view === 'payments' ? payments : refunds

  return (
    <>
      <PageHeader
        eyebrow="Finance"
        title="Payment management"
        subtitle="Every payment with its ID, amount, method and status. Online payments go through Razorpay and are verified by signature; refunds for cancelled or returned orders are logged below."
      />

      <div className="row g-3 mb-4">
        <div className="col-6 col-lg-3">
          <StatCard label="Collected" value={summary ? formatPrice(summary.collected) : '—'} icon="bi-cash-stack" dark foot={summary && `${formatPrice(summary.collectedOnline)} online · ${formatPrice(summary.collectedCash)} cash`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Refunded" value={summary ? formatPrice(summary.refunded) : '—'} icon="bi-arrow-counterclockwise" foot={summary && `${summary.refundTransactions} refund transactions · net ${formatPrice(summary.net)}`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Successful payments" value={summary?.successful ?? '—'} icon="bi-check2-circle" foot={summary && `${summary.failed} failed`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Cash on delivery due" value={summary?.codDue ?? '—'} icon="bi-truck" foot={summary && `${formatPrice(summary.codDueAmount)} to collect`} />
        </div>
      </div>

      <div className="mb-3">
        <Segment label="View" options={VIEWS} value={view} onChange={setView} />
      </div>

      <div className="ss-card overflow-hidden">
        {view === 'payments' && (
          <div className="ss-toolbar flex-wrap">
            <div className="ss-toolbar-search">
              <i className="bi bi-search" aria-hidden="true" />
              <input
                type="search"
                className="form-control form-control-sm"
                placeholder="Order number, customer or Razorpay ID"
                aria-label="Search payments"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <Segment label="Payment status" options={FILTERS} value={filter} onChange={setFilter} />
            <select className="form-select form-select-sm ms-auto" style={{ maxWidth: 190 }} aria-label="Filter by payment method" value={method} onChange={(e) => setMethod(e.target.value)}>
              <option value="">All methods</option>
              <option value="RAZORPAY">Online (Razorpay)</option>
              <option value="COD">Cash on delivery</option>
            </select>
          </div>
        )}

        {list.error ? (
          <ErrorState message={list.error} onRetry={list.reload} />
        ) : !list.result ? (
          <PageLoader />
        ) : list.result.content.length === 0 ? (
          <EmptyState icon={view === 'payments' ? 'bi-credit-card' : 'bi-arrow-counterclockwise'} title={view === 'payments' ? 'No payments' : 'No refunds yet'}>
            {view === 'payments' ? 'Nothing matches these filters.' : 'Refunds appear here as soon as they are issued.'}
          </EmptyState>
        ) : view === 'payments' ? (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Payment ID</th>
                  <th>Order &amp; customer</th>
                  <th>Method</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {payments.result.content.map((p) => (
                  <tr key={p.paymentId}>
                    <td className="text-nowrap">
                      <div className="ss-cell-title">#{p.paymentId}</div>
                      <div className="ss-cell-sub">{p.razorpayPaymentId || (p.method === 'COD' ? 'Cash' : '—')}</div>
                      <div className="ss-cell-sub">{formatDateTime(p.createdAt)}</div>
                    </td>
                    <td style={{ maxWidth: 190 }}>
                      <div className="small fw-semibold">{p.orderNumber}</div>
                      <div className="ss-cell-sub">{p.customerName}</div>
                      <div className="ss-cell-sub">Order {labelize(p.orderStatus).toLowerCase()}</div>
                    </td>
                    <td>
                      <MethodCell p={p} />
                    </td>
                    <td className="text-nowrap">
                      <div className="fw-semibold">{formatPrice(p.amount)}</div>
                      {Number(p.refundedAmount) > 0 && <div className="small text-success">Refunded {formatPrice(p.refundedAmount)}</div>}
                    </td>
                    <td>
                      <StatusBadge status={p.status} label={p.status === 'PENDING' && p.method === 'COD' ? 'Collect on delivery' : STATUS_LABELS[p.status]} />
                      {p.failureReason && p.status !== 'SUCCESS' && (
                        <div className="ss-cell-sub mt-1" style={{ maxWidth: 170 }}>
                          {p.failureReason}
                        </div>
                      )}
                    </td>
                    <td className="text-end text-nowrap">
                      {p.refundEligible ? (
                        <button type="button" className="btn btn-sm btn-danger-soft" onClick={() => openRefund(p)}>
                          <i className="bi bi-arrow-counterclockwise me-1" aria-hidden="true" />
                          Refund {formatPrice(p.refundable)}
                        </button>
                      ) : (
                        <span className="small text-muted">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Refund ID</th>
                  <th>Payment / order</th>
                  <th>Amount</th>
                  <th>How</th>
                  <th>Reason</th>
                  <th>Issued by</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {refunds.result.content.map((r) => (
                  <tr key={r.refundId}>
                    <td className="text-nowrap">
                      <div className="ss-cell-title">R-{r.refundId}</div>
                      <div className="ss-cell-sub">{formatDateTime(r.createdAt)}</div>
                    </td>
                    <td>
                      <div className="small fw-semibold">
                        #{r.paymentId} · {r.orderNumber}
                      </div>
                      <div className="ss-cell-sub">{r.customerName}</div>
                    </td>
                    <td className="fw-semibold text-nowrap">{formatPrice(r.amount)}</td>
                    <td className="small">
                      {r.mode === 'RAZORPAY' ? 'Razorpay → original method' : 'Manual (bank / UPI transfer)'}
                      {r.razorpayRefundId && <div className="ss-cell-sub">{r.razorpayRefundId}</div>}
                    </td>
                    <td className="small" style={{ maxWidth: 240 }}>
                      {r.reason}
                    </td>
                    <td className="small">{r.initiatedBy}</td>
                    <td>
                      <StatusBadge status={r.status === 'PROCESSED' ? 'SUCCESS' : 'FAILED'} label={r.status === 'PROCESSED' ? 'Processed' : 'Failed'} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {list.result && <Pagination page={list.result.page} totalPages={list.result.totalPages} onChange={list.setPage} />}

      <Modal
        open={Boolean(target)}
        title={target && `Refund payment #${target.paymentId}`}
        subtitle={target && `Order ${target.orderNumber} · ${target.customerName} · order ${labelize(target.orderStatus).toLowerCase()}`}
        onClose={() => !busy && setTarget(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setTarget(null)} disabled={busy}>
              Cancel
            </button>
            <button type="submit" form="refund-form" className="btn btn-danger-soft" disabled={busy}>
              {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              Issue refund
            </button>
          </>
        }
      >
        {target && (
          <form id="refund-form" onSubmit={submitRefund} noValidate className="d-grid gap-3">
            {formError && <Alert variant="danger">{formError}</Alert>}
            <div className="ss-summary-row">
              <span>Paid ({target.instrumentLabel || target.method})</span>
              <span>{formatPrice(target.amount)}</span>
            </div>
            <div className="ss-summary-row text-success">
              <span>Already refunded</span>
              <span>{formatPrice(target.refundedAmount)}</span>
            </div>
            <div className="ss-summary-row fw-semibold">
              <span>Can still be refunded</span>
              <span>{formatPrice(target.refundable)}</span>
            </div>
            {history.length > 0 && (
              <div className="small text-muted">
                Earlier refunds:{' '}
                {history.map((h) => `${formatPrice(h.amount)} (${h.reason}${h.status === 'FAILED' ? ', failed' : ''})`).join('; ')}
              </div>
            )}
            <Field
              id="refund-amount"
              type="number"
              min="1"
              step="0.01"
              label="Refund amount (₹)"
              value={form.amount}
              onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))}
              error={errors.amount}
            />
            <Field
              id="refund-reason"
              as="textarea"
              rows={2}
              maxLength={255}
              label="Reason (shown in the refund log)"
              placeholder="e.g. Delivery fee for the returned order"
              value={form.reason}
              onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))}
              error={errors.reason}
            />
            <Alert variant="warning" className="mb-0">
              {target.method === 'COD'
                ? 'This order was paid in cash, so the refund is recorded for the finance team to transfer to the customer (bank or UPI). '
                : 'The money goes back to the customer’s original payment method through Razorpay, usually within 5-7 working days. '}
              Refunds cannot be undone. The customer is notified and the refund is logged.
            </Alert>
          </form>
        )}
      </Modal>
    </>
  )
}
