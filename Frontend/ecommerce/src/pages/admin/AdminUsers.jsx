import { useState } from 'react'
import adminService from '../../services/adminService'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { ActiveBadge, EmptyState, ErrorState, PageHeader, PageLoader, Segment } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import AddAdminModal from './AddAdminModal'
import { formatDate, labelize } from '../../utils/format'

const ROLES = [
  { value: '', label: 'Everyone' },
  { value: 'CUSTOMER', label: 'Customers' },
  { value: 'SELLER', label: 'Sellers' },
  { value: 'DELIVERY_PARTNER', label: 'Delivery partners' },
  { value: 'ADMIN', label: 'Admins' },
]

/** Every account on the platform. Admins can suspend (block sign-in) or reactivate any other account. */
export default function AdminUsers() {
  const { user: me } = useAuth()
  const toast = useToast()
  const [role, setRole] = useState('')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => adminService.getUsers({ role, search: debouncedSearch, page, size: 15 }),
    [role, debouncedSearch],
  )
  const [target, setTarget] = useState(null)
  const [busy, setBusy] = useState(false)
  const [addingAdmin, setAddingAdmin] = useState(false)

  const toggle = async () => {
    setBusy(true)
    try {
      const updated = await adminService.setUserActive(target.id, !target.active)
      setResult((r) => ({ ...r, content: r.content.map((u) => (u.id === updated.id ? updated : u)) }))
      toast.success(`${updated.fullName} ${updated.active ? 'can sign in again' : 'has been suspended'}.`)
      setTarget(null)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="People"
        title="Users"
        subtitle="All customer, seller, delivery partner and admin accounts. Suspended accounts cannot sign in."
        actions={
          <button type="button" className="btn btn-ink" onClick={() => setAddingAdmin(true)}>
            <i className="bi bi-person-plus me-2" aria-hidden="true" />
            Add admin
          </button>
        }
      />

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Search name, email or store"
              aria-label="Search users"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <Segment label="Role" options={ROLES} value={role} onChange={setRole} />
        </div>

        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-people" title="No users found">
            No accounts match these filters.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Contact</th>
                  <th>Role</th>
                  <th>Joined</th>
                  <th>Status</th>
                  <th className="text-end">Action</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <div className="ss-cell-title">{u.fullName}</div>
                      {u.storeName && <div className="ss-cell-sub">{u.storeName}</div>}
                    </td>
                    <td>
                      <div className="small">{u.email}</div>
                      <div className="ss-cell-sub">{u.phone || '—'}</div>
                    </td>
                    <td>
                      <span className="small fw-semibold">{labelize(u.role)}</span>
                      {u.role === 'SELLER' && <div className="ss-cell-sub">Store {labelize(u.sellerStatus).toLowerCase()}</div>}
                      {u.role === 'DELIVERY_PARTNER' && <div className="ss-cell-sub">Application {labelize(u.sellerStatus).toLowerCase()}</div>}
                    </td>
                    <td className="small text-muted">{formatDate(u.createdAt)}</td>
                    <td>
                      <div className="d-flex flex-column align-items-start gap-1">
                        <ActiveBadge active={u.active} on="Active" off="Suspended" />
                        {!u.emailVerified && <span className="ss-pill ss-pill-warning">Email unverified</span>}
                      </div>
                    </td>
                    <td className="text-end">
                      {u.id === me.id ? (
                        <span className="small text-muted">You</span>
                      ) : (
                        <button type="button" className={`btn btn-sm ${u.active ? 'btn-danger-soft' : 'btn-outline-ink'}`} onClick={() => setTarget(u)}>
                          {u.active ? 'Suspend' : 'Reactivate'}
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

      <AddAdminModal
        open={addingAdmin}
        onClose={() => setAddingAdmin(false)}
        onCreated={() => {
          setAddingAdmin(false)
          setRole('ADMIN')
          reload()
        }}
      />

      <ConfirmDialog
        open={Boolean(target)}
        title={target?.active ? `Suspend ${target?.fullName}?` : `Reactivate ${target?.fullName}?`}
        confirmLabel={target?.active ? 'Suspend' : 'Reactivate'}
        danger={target?.active}
        busy={busy}
        onConfirm={toggle}
        onCancel={() => setTarget(null)}
      >
        {target?.active
          ? 'They will be signed out and unable to sign in until reactivated. Their order history is kept.'
          : 'They will be able to sign in again.'}
      </ConfirmDialog>
    </>
  )
}
