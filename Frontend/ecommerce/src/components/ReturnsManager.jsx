import { useState } from 'react'
import { useToast } from '../context/ToastContext'
import usePagedList from '../hooks/usePagedList'
import Modal from './Modal'
import Pagination from './Pagination'
import { Field } from './FormControls'
import { Alert, EmptyState, ErrorState, PageLoader, SafeImage, Segment, StatusBadge } from './ui'
import { getErrorMessage } from '../utils/errors'
import { formatDateTime, formatPrice, labelize } from '../utils/format'

const FILTERS = [
  { value: 'REQUESTED', label: 'To review' },
  { value: 'REFUNDED', label: 'Refunded' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: '', label: 'All' },
]

/**
 * Return requests with approve / reject actions. Used by the seller studio (own items only)
 * and the admin console (every store).
 * fetchPage({ status, page, size }) and decide(id, approve, note) come from the matching service.
 */
export default function ReturnsManager({ fetchPage, decide, showStore = false }) {
  const toast = useToast()
  const [status, setStatus] = useState('REQUESTED')
  const { result, error, reload, setPage, setResult } = usePagedList((page) => fetchPage({ status, page, size: 10 }), [status])
  const [target, setTarget] = useState(null) // { request, approve }
  const [note, setNote] = useState('')
  const [formError, setFormError] = useState(null)
  const [busy, setBusy] = useState(false)

  const open = (request, approve) => {
    setTarget({ request, approve })
    setNote('')
    setFormError(null)
  }

  const submit = async (event) => {
    event.preventDefault()
    if (!target.approve && !note.trim()) {
      setFormError('Tell the customer why the return is rejected.')
      return
    }
    setBusy(true)
    try {
      const updated = await decide(target.request.returnId, target.approve, note.trim() || null)
      setResult((r) => ({ ...r, content: r.content.map((x) => (x.returnId === updated.returnId ? updated : x)) }))
      toast.success(`Return for ${updated.productName} ${labelize(updated.status).toLowerCase()}.`)
      setTarget(null)
    } catch (err) {
      setFormError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <Segment label="Return status" options={FILTERS} value={status} onChange={setStatus} />
        </div>
        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-arrow-counterclockwise" title={status === 'REQUESTED' ? 'No returns to review' : 'No returns'}>
            {status === 'REQUESTED' ? 'New return requests from customers will appear here.' : 'Nothing matches this filter.'}
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Customer &amp; reason</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th className="text-end">Decision</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((r) => (
                  <tr key={r.returnId}>
                    <td>
                      <div className="d-flex align-items-center gap-2">
                        <div className="ss-thumb">
                          <SafeImage src={r.imageUrl} alt="" />
                        </div>
                        <div className="min-w-0">
                          <div className="ss-cell-title">{r.productName}</div>
                          <div className="ss-cell-sub">
                            {r.orderNumber} · qty {r.quantity}
                            {showStore && ` · ${r.storeName}`}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td style={{ minWidth: 200 }}>
                      <div className="fw-semibold small">{r.customerName}</div>
                      <div className="small">{labelize(r.reason)}</div>
                      {r.comments && <div className="ss-cell-sub">“{r.comments}”</div>}
                      <div className="ss-cell-sub">{formatDateTime(r.createdAt)}</div>
                    </td>
                    <td>
                      <div className="fw-semibold">{formatPrice(r.lineTotal)}</div>
                      {r.refundAmount != null && <div className="small text-success">Refund {formatPrice(r.refundAmount)}</div>}
                    </td>
                    <td>
                      <StatusBadge status={r.status} label={r.status === 'REQUESTED' ? 'To review' : undefined} />
                      {r.resolutionNote && <div className="ss-cell-sub mt-1">{r.resolutionNote}</div>}
                    </td>
                    <td className="text-end text-nowrap">
                      {r.status === 'REQUESTED' ? (
                        <>
                          <button type="button" className="btn btn-sm btn-ink me-1" onClick={() => open(r, true)}>
                            Approve
                          </button>
                          <button type="button" className="btn btn-sm btn-danger-soft" onClick={() => open(r, false)}>
                            Reject
                          </button>
                        </>
                      ) : (
                        <span className="small text-muted">{formatDateTime(r.resolvedAt)}</span>
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
        open={Boolean(target)}
        title={target?.approve ? 'Approve this return?' : 'Reject this return?'}
        subtitle={target && `${target.request.productName} · ${target.request.orderNumber}`}
        onClose={() => !busy && setTarget(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setTarget(null)} disabled={busy}>
              Back
            </button>
            <button type="submit" form="return-form" className={`btn ${target?.approve ? 'btn-ink' : 'btn-danger-soft'}`} disabled={busy}>
              {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              {target?.approve ? 'Approve & refund' : 'Reject return'}
            </button>
          </>
        }
      >
        <form id="return-form" onSubmit={submit} noValidate className="d-grid gap-3">
          {formError && <Alert variant="danger">{formError}</Alert>}
          {target?.approve && (
            <Alert variant="info">
              The item goes back into stock. Online payments are refunded through Razorpay; cash-on-delivery refunds are
              recorded for a manual bank transfer.
            </Alert>
          )}
          <Field
            id="return-note"
            as="textarea"
            rows={3}
            maxLength={255}
            label={target?.approve ? 'Note to the customer (optional)' : 'Reason for rejecting'}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </form>
      </Modal>
    </>
  )
}
