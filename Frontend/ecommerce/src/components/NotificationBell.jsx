import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import notificationService from '../services/notificationService'
import { useAuth } from '../context/AuthContext'
import { useLiveEvent } from '../context/LiveContext'
import { formatDateTime } from '../utils/format'

const POLL_MS = 60000

/** Where the full notification history lives for each role. */
const HISTORY_PATH = { SELLER: '/seller/notifications', ADMIN: '/admin/notifications', DELIVERY_PARTNER: '/delivery/notifications' }

/** Bell icon with unread count and a dropdown of the latest notifications. */
export default function NotificationBell({ className = 'ss-icon-btn' }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [unread, setUnread] = useState(0)
  const [items, setItems] = useState(null)
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  const refreshCount = useCallback(() => {
    notificationService.unreadCount().then(setUnread).catch(() => {})
  }, [])

  // A new notification arrives live: bump the badge immediately.
  useLiveEvent((type) => {
    if (type === 'notification') setUnread((n) => n + 1)
  })

  useEffect(() => {
    if (!user) return undefined
    refreshCount()
    const timer = setInterval(refreshCount, POLL_MS)
    return () => clearInterval(timer)
  }, [user, refreshCount])

  useEffect(() => {
    const onClick = (e) => !ref.current?.contains(e.target) && setOpen(false)
    const onKey = (e) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [])

  if (!user) return null

  const toggle = () => {
    const next = !open
    setOpen(next)
    if (next) {
      setItems(null)
      notificationService
        .list(0, 8)
        .then((page) => setItems(page.content))
        .catch(() => setItems([]))
    }
  }

  const openItem = async (n) => {
    setOpen(false)
    if (!n.read) {
      await notificationService.markRead(n.notificationId).catch(() => {})
      refreshCount()
    }
    if (n.link) navigate(n.link)
  }

  const markAll = async () => {
    await notificationService.markAllRead().catch(() => {})
    setItems((list) => list?.map((n) => ({ ...n, read: true })))
    setUnread(0)
  }

  return (
    <div className="ss-menu-wrap" ref={ref}>
      <button
        type="button"
        className={className}
        onClick={toggle}
        aria-expanded={open}
        aria-haspopup="true"
        aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}
      >
        <i className="bi bi-bell" aria-hidden="true" />
        <span>Alerts</span>
        {unread > 0 && <span className="ss-badge">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div className="ss-menu ss-notify-menu">
          <div className="d-flex justify-content-between align-items-center px-2 py-1">
            <strong className="small">Notifications</strong>
            {unread > 0 && (
              <button type="button" className="btn-link-ink small w-auto p-0" onClick={markAll}>
                Mark all read
              </button>
            )}
          </div>
          {items === null ? (
            <div className="text-center py-3">
              <span className="spinner-border spinner-border-sm" aria-label="Loading" />
            </div>
          ) : items.length === 0 ? (
            <p className="small text-muted text-center py-3 mb-0">You&apos;re all caught up.</p>
          ) : (
            items.map((n) => (
              <button type="button" key={n.notificationId} className={`ss-notify-item ${n.read ? '' : 'unread'}`} onClick={() => openItem(n)}>
                <span className="fw-semibold small d-block">{n.title}</span>
                <span className="small text-muted d-block">{n.message}</span>
                <span className="text-muted d-block" style={{ fontSize: '0.7rem' }}>
                  {formatDateTime(n.createdAt)}
                </span>
              </button>
            ))
          )}
          <Link to={HISTORY_PATH[user.role] || '/account/notifications'} className="d-block text-center small fw-semibold py-2 border-top mt-1" onClick={() => setOpen(false)}>
            View all notifications
          </Link>
        </div>
      )}
    </div>
  )
}
