import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { ActiveBadge, EmptyState, ErrorState, PageHeader, PageLoader, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate } from '../../utils/format'

const SELLER_FILTERS = ['', 'PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED']

/** Seller applications and store status (approve / reject / suspend). Account sign-in is managed on the Users page. */
export default function AdminSellers() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const sellerStatus = SELLER_FILTERS.includes(params.get('status')) ? params.get('status') : ''
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [pending, setPending] = useState(null) // { user, action }
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setError(null)
    setResult(null)
    adminService
      .getUsers({
        role: 'SELLER',
        sellerStatus,
        search: debouncedSearch,
        page,
        size: 10,
      })
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
  }, [sellerStatus, debouncedSearch, page])

  useEffect(() => {
    load()
  }, [load])

  const switchTo = (next) => {
    setPage(0)
    setParams(next)
  }

  const run = async () => {
    const { user, action } = pending
    setBusy(true)
    try {
      await adminService.setSellerStatus(user.id, action.sellerStatus)
      toast.success(`${user.storeName || user.fullName}: ${action.done}`)
      setPending(null)
      load()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const sellerActions = (u) => {
    switch (u.sellerStatus) {
      case 'PENDING':
        return [
          { label: 'Approve', sellerStatus: 'APPROVED', done: 'store approved', style: 'btn-ink' },
          { label: 'Reject', sellerStatus: 'REJECTED', done: 'application rejected', style: 'btn-danger-soft', danger: true },
        ]
      case 'APPROVED':
        return [{ label: 'Suspend', sellerStatus: 'SUSPENDED', done: 'store suspended', style: 'btn-danger-soft', danger: true }]
      default:
        return [{ label: 'Approve', sellerStatus: 'APPROVED', done: 'store approved', style: 'btn-outline-ink' }]
    }
  }

  return (
    <>
      <PageHeader eyebrow="Marketplace" title="Sellers" subtitle="Review seller applications. Suspending a store hides all of its products from customers." />

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Search store, name or email"
              aria-label="Search people"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
            />
          </div>
          <select
            className="form-select form-select-sm ms-auto"
            style={{ maxWidth: 200 }}
            aria-label="Filter by seller status"
            value={sellerStatus}
            onChange={(e) => switchTo(e.target.value ? { status: e.target.value } : {})}
          >
            {SELLER_FILTERS.map((s) => (
              <option key={s} value={s}>
                {s ? s.charAt(0) + s.slice(1).toLowerCase() : 'All statuses'}
              </option>
            ))}
          </select>
        </div>

        {error ? (
          <ErrorState message={error} onRetry={load} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-people" title="Nobody here yet">
            {sellerStatus === 'PENDING' ? 'No applications are waiting for review.' : 'No accounts match these filters.'}
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Store</th>
                  <th>Contact</th>
                  <th>Joined</th>
                  <th>Email</th>
                  <th>Store status</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <div className="ss-cell-title">{u.storeName}</div>
                      <div className="ss-cell-sub">{u.fullName}</div>
                    </td>
                    <td>
                      <div className="small">{u.email}</div>
                      <div className="ss-cell-sub">{u.phone || '—'}</div>
                    </td>
                    <td className="small text-muted">{formatDate(u.createdAt)}</td>
                    <td>
                      <ActiveBadge active={u.emailVerified} on="Verified" off="Unverified" />
                    </td>
                    <td>
                      <StatusBadge status={u.sellerStatus} />
                      {!u.active && <div className="small text-danger mt-1">Account suspended</div>}
                    </td>
                    <td className="text-end text-nowrap">
                      {sellerActions(u).map((a) => (
                        <button
                          type="button"
                          key={a.label}
                          className={`btn btn-sm ms-2 ${a.style}`}
                          onClick={() => setPending({ user: u, action: a })}
                        >
                          {a.label}
                        </button>
                      ))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}

      <ConfirmDialog
        open={Boolean(pending)}
        title={`${pending?.action.label} ${pending?.user.storeName || pending?.user.fullName}?`}
        confirmLabel={pending?.action.label}
        danger={pending?.action.danger}
        busy={busy}
        onConfirm={run}
        onCancel={() => setPending(null)}
      >
        {pending?.action.sellerStatus === 'APPROVED' && 'The seller will be able to publish products immediately.'}
        {pending?.action.sellerStatus === 'REJECTED' && 'The seller will not be able to list products.'}
        {pending?.action.sellerStatus === 'SUSPENDED' && 'All of this store’s products will be hidden from customers.'}
      </ConfirmDialog>
    </>
  )
}
