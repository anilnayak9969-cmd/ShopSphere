import { useNavigate } from 'react-router-dom'
import notificationService from '../../services/notificationService'
import usePagedList from '../../hooks/usePagedList'
import Pagination from '../../components/Pagination'
import { EmptyState, ErrorState, PageLoader } from '../../components/ui'
import { formatDateTime } from '../../utils/format'

/** Full notification history for any signed-in role. */
export default function Notifications() {
  const navigate = useNavigate()
  const { result, error, reload, setPage, setResult } = usePagedList((page) => notificationService.list(page, 15))

  const open = async (n) => {
    if (!n.read) await notificationService.markRead(n.notificationId).catch(() => {})
    if (n.link) navigate(n.link)
  }

  const markAll = async () => {
    await notificationService.markAllRead().catch(() => {})
    setResult((r) => r && { ...r, content: r.content.map((n) => ({ ...n, read: true })) })
  }

  return (
    <>
      <div className="d-flex justify-content-between align-items-end flex-wrap gap-2 mb-3">
        <div>
          <span className="ss-eyebrow">Updates</span>
          <h1 className="h3 mt-1 mb-0">Notifications</h1>
        </div>
        {result?.content.some((n) => !n.read) && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={markAll}>
            Mark all as read
          </button>
        )}
      </div>
      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !result ? (
        <PageLoader />
      ) : result.content.length === 0 ? (
        <div className="ss-card">
          <EmptyState icon="bi-bell" title="No notifications">
            Order updates, approvals and return decisions will appear here.
          </EmptyState>
        </div>
      ) : (
        <>
          <div className="ss-card p-2">
            {result.content.map((n) => (
              <button type="button" key={n.notificationId} className={`ss-notify-item w-100 text-start border-0 bg-transparent p-3 ${n.read ? '' : 'unread'}`} onClick={() => open(n)}>
                <span className="fw-semibold d-block">{n.title}</span>
                <span className="small text-muted d-block">{n.message}</span>
                <span className="small text-muted">{formatDateTime(n.createdAt)}</span>
              </button>
            ))}
          </div>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </>
  )
}
