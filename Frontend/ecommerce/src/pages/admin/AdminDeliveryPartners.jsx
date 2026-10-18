import { useState } from 'react'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { ActiveBadge, EmptyState, ErrorState, PageHeader, PageLoader, Segment, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate, labelize } from '../../utils/format'
import { VEHICLE_TYPES } from '../../utils/roles'

const FILTERS = [
  { value: 'PENDING', label: 'To review' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: '', label: 'All' },
]

const ACTIONS = {
  PENDING: [
    { label: 'Approve', status: 'APPROVED', style: 'btn-ink' },
    { label: 'Reject', status: 'REJECTED', style: 'btn-danger-soft', danger: true },
  ],
  APPROVED: [{ label: 'Suspend', status: 'SUSPENDED', style: 'btn-danger-soft', danger: true }],
  REJECTED: [{ label: 'Approve', status: 'APPROVED', style: 'btn-outline-ink' }],
  SUSPENDED: [{ label: 'Reinstate', status: 'APPROVED', style: 'btn-outline-ink' }],
}

/** Delivery partner applications: review vehicle and service area, then approve, reject or suspend. */
export default function AdminDeliveryPartners() {
  const toast = useToast()
  const [status, setStatus] = useState('PENDING')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => adminService.getDeliveryPartners({ status, search: debouncedSearch, page, size: 10 }),
    [status, debouncedSearch],
  )
  const [pending, setPending] = useState(null) // { partner, action }
  const [busy, setBusy] = useState(false)

  const run = async () => {
    setBusy(true)
    try {
      const updated = await adminService.setDeliveryPartnerStatus(pending.partner.userId, pending.action.status)
      setResult((r) => ({
        ...r,
        content: status ? r.content.filter((p) => p.userId !== updated.userId) : r.content.map((p) => (p.userId === updated.userId ? updated : p)),
      }))
      toast.success(`${updated.fullName}: application ${labelize(updated.status).toLowerCase()}.`)
      setPending(null)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader eyebrow="People" title="Delivery partners" subtitle="Review applications. Identity documents are checked in person at onboarding, so only vehicle and service-area details are stored." />
      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Search name, email, phone or city"
              aria-label="Search delivery partners"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <Segment label="Application status" options={FILTERS} value={status} onChange={setStatus} />
        </div>
        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-bicycle" title={status === 'PENDING' ? 'No applications to review' : 'No delivery partners'}>
            {status === 'PENDING' ? 'New applications from the Register page appear here.' : 'Nothing matches these filters.'}
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Partner</th>
                  <th>Vehicle</th>
                  <th>Service area</th>
                  <th>Applied</th>
                  <th>Status</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((p) => (
                  <tr key={p.userId}>
                    <td>
                      <div className="ss-cell-title">{p.fullName}</div>
                      <div className="ss-cell-sub">
                        {p.email} · {p.phone}
                      </div>
                    </td>
                    <td>
                      <div className="small fw-semibold">{VEHICLE_TYPES.find((v) => v.value === p.vehicleType)?.label}</div>
                      <div className="ss-cell-sub">{p.vehicleNumber || 'No registration needed'}</div>
                    </td>
                    <td className="small">
                      {p.serviceCity} · {p.servicePincode}
                    </td>
                    <td className="small text-muted">{formatDate(p.appliedAt)}</td>
                    <td>
                      <div className="d-flex flex-column align-items-start gap-1">
                        <StatusBadge status={p.status} />
                        {!p.emailVerified && <ActiveBadge active={false} off="Email unverified" />}
                      </div>
                    </td>
                    <td className="text-end text-nowrap">
                      {(ACTIONS[p.status] || []).map((a) => (
                        <button type="button" key={a.label} className={`btn btn-sm ms-1 ${a.style}`} onClick={() => setPending({ partner: p, action: a })}>
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
        title={`${pending?.action.label} ${pending?.partner.fullName}?`}
        confirmLabel={pending?.action.label}
        danger={pending?.action.danger}
        busy={busy}
        onConfirm={run}
        onCancel={() => setPending(null)}
      >
        The partner is notified of this decision on their dashboard.
      </ConfirmDialog>
    </>
  )
}
