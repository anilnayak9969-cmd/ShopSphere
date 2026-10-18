import { useState } from 'react'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import usePagedList from '../../hooks/usePagedList'
import Pagination from '../../components/Pagination'
import { EmptyState, ErrorState, PageHeader, PageLoader, Segment } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDateTime } from '../../utils/format'

const FILTERS = [
  { value: 'open', label: 'Open' },
  { value: 'resolved', label: 'Resolved' },
  { value: 'all', label: 'All' },
]

/** Messages sent from the Contact page. */
export default function AdminMessages() {
  const toast = useToast()
  const [filter, setFilter] = useState('open')
  const resolved = filter === 'all' ? undefined : filter === 'resolved'
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => adminService.getMessages({ resolved, page, size: 10 }),
    [filter],
  )
  const [busyId, setBusyId] = useState(null)

  const toggle = async (m) => {
    setBusyId(m.messageId)
    try {
      const updated = await adminService.setMessageResolved(m.messageId, !m.resolved)
      setResult((r) => ({
        ...r,
        content:
          filter === 'all'
            ? r.content.map((x) => (x.messageId === updated.messageId ? updated : x))
            : r.content.filter((x) => x.messageId !== updated.messageId),
      }))
      toast.success(updated.resolved ? 'Marked as resolved.' : 'Reopened.')
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      <PageHeader eyebrow="Support" title="Messages" subtitle="Questions sent through the Contact page. Reply by email, then mark the message as resolved." />
      <div className="mb-3">
        <Segment label="Message filter" options={FILTERS} value={filter} onChange={setFilter} />
      </div>
      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !result ? (
        <PageLoader />
      ) : result.content.length === 0 ? (
        <div className="ss-card">
          <EmptyState icon="bi-envelope-open" title={filter === 'open' ? 'Inbox zero' : 'No messages'}>
            {filter === 'open' ? 'There are no open messages.' : 'Nothing matches this filter.'}
          </EmptyState>
        </div>
      ) : (
        <div className="d-grid gap-3">
          {result.content.map((m) => (
            <article key={m.messageId} className="ss-card ss-card-pad">
              <div className="d-flex justify-content-between gap-3 flex-wrap">
                <div className="min-w-0">
                  <h2 className="h6 mb-1">{m.subject}</h2>
                  <div className="small text-muted">
                    {m.name} · <a href={`mailto:${m.email}?subject=${encodeURIComponent(`Re: ${m.subject}`)}`}>{m.email}</a> · {formatDateTime(m.createdAt)}
                  </div>
                </div>
                <button
                  type="button"
                  className={`btn btn-sm ${m.resolved ? 'btn-ghost' : 'btn-ink'}`}
                  disabled={busyId === m.messageId}
                  onClick={() => toggle(m)}
                >
                  {m.resolved ? 'Reopen' : 'Mark resolved'}
                </button>
              </div>
              <p className="mt-3 mb-0 small" style={{ whiteSpace: 'pre-line' }}>
                {m.message}
              </p>
            </article>
          ))}
        </div>
      )}
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}
    </>
  )
}
