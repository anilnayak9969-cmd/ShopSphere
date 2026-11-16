import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import Modal, { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field, PasswordInput } from '../../components/FormControls'
import { ActiveBadge, Alert, EmptyState, ErrorState, PageHeader, PageLoader, Segment, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDate, formatDateTime, formatPrice } from '../../utils/format'
import { EMAIL_RE, PASSWORD_MESSAGE, PASSWORD_RE, PERSON_NAME_RE, PHONE_RE } from '../../utils/validators'

/**
 * Customer Management module — the admin Customer dashboard.
 * View every customer with their contact details and order history, add customers by hand,
 * edit contact details and account status, and deactivate (soft delete) or reactivate accounts.
 */
const STATUS_TABS = [
  { value: '', label: 'All' },
  { value: 'true', label: 'Active' },
  { value: 'false', label: 'Inactive' },
]

const EMPTY_FORM = { fullName: '', email: '', phone: '', password: '', active: true }

function validate(form, creating) {
  const e = {}
  const name = form.fullName.trim()
  if (name.length < 2 || name.length > 100) e.fullName = 'Full name must be 2-100 characters'
  else if (!PERSON_NAME_RE.test(name)) e.fullName = "Use letters, spaces and . ' - only"
  if (!EMAIL_RE.test(form.email.trim())) e.email = 'Enter a valid email address'
  if (!PHONE_RE.test(form.phone)) e.phone = 'Enter a valid 10-digit mobile number starting with 6-9'
  if (creating && !PASSWORD_RE.test(form.password)) e.password = PASSWORD_MESSAGE
  return e
}

export default function AdminCustomers() {
  const toast = useToast()
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const [status, setStatus] = useState('')
  const [summary, setSummary] = useState(null)
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => adminService.getCustomers({ search: debouncedSearch, active: status, page, size: 10 }),
    [debouncedSearch, status],
  )

  const [editor, setEditor] = useState(null) // { mode: 'create' | 'edit', customer? }
  const [form, setForm] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [detail, setDetail] = useState(null)
  const [toggleTarget, setToggleTarget] = useState(null)

  const loadSummary = useCallback(() => {
    adminService.getCustomerSummary().then(setSummary).catch(() => setSummary(null))
  }, [])
  useEffect(() => {
    loadSummary()
  }, [loadSummary])

  const replace = (updated) => {
    setResult((r) => ({
      ...r,
      content: status && String(updated.active) !== status
        ? r.content.filter((c) => c.id !== updated.id)
        : r.content.map((c) => (c.id === updated.id ? updated : c)),
    }))
    loadSummary()
  }

  const openCreate = () => {
    setForm(EMPTY_FORM)
    setErrors({})
    setFormError(null)
    setEditor({ mode: 'create' })
  }

  const openEdit = (customer) => {
    setForm({ fullName: customer.fullName, email: customer.email, phone: customer.phone || '', password: '', active: customer.active })
    setErrors({})
    setFormError(null)
    setEditor({ mode: 'edit', customer })
  }

  const openDetail = async (customer) => {
    try {
      setDetail(await adminService.getCustomer(customer.id))
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  const save = async (event) => {
    event.preventDefault()
    const creating = editor.mode === 'create'
    const found = validate(form, creating)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return
    setSaving(true)
    try {
      if (creating) {
        const created = await adminService.createCustomer({
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          phone: form.phone,
          password: form.password,
        })
        toast.success(`Customer "${created.fullName}" added. They can sign in now.`)
        setStatus('')
        setSearch('')
        reload()
        loadSummary()
      } else {
        const updated = await adminService.updateCustomer(editor.customer.id, {
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          phone: form.phone,
          active: form.active,
        })
        toast.success(`Customer "${updated.fullName}" updated.`)
        replace(updated)
      }
      setEditor(null)
    } catch (err) {
      setErrors(getFieldErrors(err))
      setFormError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const toggle = async () => {
    setSaving(true)
    try {
      const updated = await adminService.setCustomerActive(toggleTarget.id, !toggleTarget.active)
      replace(updated)
      if (detail?.customer.id === updated.id) setDetail((d) => ({ ...d, customer: updated }))
      toast.success(updated.active ? `"${updated.fullName}" is active again.` : `"${updated.fullName}" has been deactivated.`)
      setToggleTarget(null)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  // Typing in a field clears its error message.
  const change = (field, value) => {
    setForm((f) => ({ ...f, [field]: value }))
    setErrors((errs) => (errs[field] ? { ...errs, [field]: undefined } : errs))
  }
  const set = (field) => (e) => change(field, e.target.value)
  const creating = editor?.mode === 'create'

  return (
    <>
      <PageHeader
        eyebrow="People"
        title="Customer management"
        subtitle="Every customer with their contact details and order history. Add customers, update their details and deactivate accounts."
        actions={
          <button type="button" className="btn btn-ink" onClick={openCreate}>
            <i className="bi bi-person-plus me-2" aria-hidden="true" />
            Add customer
          </button>
        }
      />

      <div className="row g-3 mb-4">
        <div className="col-6 col-lg-3">
          <StatCard label="Total customers" value={summary?.total ?? '—'} icon="bi-people" foot={`${summary?.newLast30Days ?? 0} joined in the last 30 days`} />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Active" value={summary?.active ?? '—'} icon="bi-person-check" foot="Can sign in and shop" />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Inactive" value={summary?.inactive ?? '—'} icon="bi-person-slash" foot="Soft deleted, data kept" />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="With orders" value={summary?.withOrders ?? '—'} icon="bi-bag-check" foot="Placed at least one order" />
        </div>
      </div>

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Search name, email or phone"
              aria-label="Search customers"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <Segment label="Account status" options={STATUS_TABS} value={status} onChange={setStatus} />
        </div>

        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-people" title="No customers found">
            No customers match these filters.
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Phone</th>
                  <th>Order history</th>
                  <th>Joined</th>
                  <th>Status</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <div className="ss-cell-title">{c.fullName}</div>
                      <div className="ss-cell-sub">{c.email}</div>
                    </td>
                    <td className="text-nowrap">{c.phone || '—'}</td>
                    <td className="text-nowrap">
                      <div className="fw-semibold">
                        {c.orderCount} order{c.orderCount === 1 ? '' : 's'}
                      </div>
                      <div className="ss-cell-sub">
                        {c.orderCount ? `${formatPrice(c.totalSpent)} spent · last ${formatDate(c.lastOrderAt)}` : 'No orders yet'}
                      </div>
                    </td>
                    <td className="small text-muted text-nowrap">{formatDate(c.createdAt)}</td>
                    <td>
                      <div className="d-flex flex-column align-items-start gap-1">
                        <ActiveBadge active={c.active} />
                        {!c.emailVerified && <span className="ss-pill ss-pill-warning">Email unverified</span>}
                      </div>
                    </td>
                    <td className="text-end text-nowrap">
                      <button type="button" className="btn btn-sm btn-ghost me-1" onClick={() => openDetail(c)}>
                        View
                      </button>
                      <button type="button" className="btn btn-sm btn-outline-ink me-1" onClick={() => openEdit(c)}>
                        <i className="bi bi-pencil me-1" aria-hidden="true" />
                        Edit
                      </button>
                      <button type="button" className={`btn btn-sm ${c.active ? 'btn-danger-soft' : 'btn-outline-ink'}`} onClick={() => setToggleTarget(c)}>
                        {c.active ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}

      {/* Add / edit */}
      <Modal
        open={Boolean(editor)}
        title={creating ? 'Add a customer' : 'Edit customer'}
        subtitle={creating ? 'The customer can sign in straight away with this password.' : `Customer since ${formatDate(editor?.customer?.createdAt)}`}
        onClose={() => !saving && setEditor(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setEditor(null)} disabled={saving}>
              Cancel
            </button>
            <button type="submit" form="customer-form" className="btn btn-ink" disabled={saving}>
              {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              {creating ? 'Add customer' : 'Save changes'}
            </button>
          </>
        }
      >
        <form id="customer-form" onSubmit={save} noValidate className="d-grid gap-3">
          {formError && <Alert variant="danger">{formError}</Alert>}
          <Field id="customer-name" label="Full name (first and last name)" value={form.fullName} onChange={set('fullName')} error={errors.fullName} maxLength={100} />
          <div className="row g-3">
            <div className="col-md-7">
              <Field id="customer-email" type="email" label="Email" value={form.email} onChange={set('email')} error={errors.email} maxLength={100} />
            </div>
            <div className="col-md-5">
              <Field
                id="customer-phone"
                label="Mobile number"
                inputMode="numeric"
                maxLength={10}
                value={form.phone}
                onChange={(e) => change('phone', e.target.value.replace(/\D/g, ''))}
                error={errors.phone}
              />
            </div>
          </div>
          {creating ? (
            <>
              <PasswordInput id="customer-password" label="First password" autoComplete="new-password" value={form.password} onChange={set('password')} error={errors.password} showRules />
              <Alert variant="info" className="mb-0">
                Share the password privately and ask the customer to change it from My account. The account is marked as verified because an admin created it.
              </Alert>
            </>
          ) : (
            <div>
              <span className="form-label d-block">Account status</span>
              <div className="form-check form-switch">
                <input
                  id="customer-active"
                  type="checkbox"
                  className="form-check-input"
                  role="switch"
                  checked={form.active}
                  onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
                />
                <label htmlFor="customer-active" className="form-check-label">
                  {form.active ? 'Active — can sign in and shop' : 'Inactive — cannot sign in (data is kept)'}
                </label>
              </div>
            </div>
          )}
        </form>
      </Modal>

      {/* Customer details with order history */}
      <Modal
        open={Boolean(detail)}
        size="lg"
        title={detail?.customer.fullName}
        subtitle={detail && `Customer since ${formatDate(detail.customer.createdAt)} · last updated ${formatDateTime(detail.customer.updatedAt)}`}
        onClose={() => setDetail(null)}
        footer={
          detail && (
            <>
              <button type="button" className={`btn ${detail.customer.active ? 'btn-danger-soft' : 'btn-outline-ink'}`} onClick={() => setToggleTarget(detail.customer)}>
                {detail.customer.active ? 'Deactivate' : 'Activate'}
              </button>
              <button
                type="button"
                className="btn btn-ink"
                onClick={() => {
                  const c = detail.customer
                  setDetail(null)
                  openEdit(c)
                }}
              >
                <i className="bi bi-pencil me-1" aria-hidden="true" />
                Edit details
              </button>
            </>
          )
        }
      >
        {detail && (
          <div className="row g-4">
            <div className="col-md-5">
              <h3 className="h6">Personal information</h3>
              <dl className="small mb-3">
                <dt className="text-muted fw-normal">First name / last name</dt>
                <dd>
                  {detail.customer.firstName} / {detail.customer.lastName || '—'}
                </dd>
                <dt className="text-muted fw-normal">Email</dt>
                <dd>
                  {detail.customer.email} {detail.customer.emailVerified ? <span className="text-success">(verified)</span> : <span className="text-warning">(unverified)</span>}
                </dd>
                <dt className="text-muted fw-normal">Phone</dt>
                <dd>{detail.customer.phone || '—'}</dd>
                <dt className="text-muted fw-normal">Account status</dt>
                <dd>
                  <ActiveBadge active={detail.customer.active} />
                </dd>
              </dl>
              <h3 className="h6">Saved addresses</h3>
              {detail.addresses.length === 0 ? (
                <p className="small text-muted">No saved addresses.</p>
              ) : (
                detail.addresses.map((a) => (
                  <p key={a.addressId} className="small text-muted mb-2">
                    <strong className="text-body">{a.fullName}</strong>
                    {a.defaultAddress && <span className="ss-pill ss-pill-info ms-2">Default</span>}
                    <br />
                    {a.addressLine1}
                    {a.addressLine2 && `, ${a.addressLine2}`}, {a.city}, {a.state} {a.pincode}
                  </p>
                ))
              )}
            </div>
            <div className="col-md-7">
              <h3 className="h6">
                Order history{' '}
                <span className="text-muted fw-normal">
                  ({detail.customer.orderCount} order{detail.customer.orderCount === 1 ? '' : 's'} · {formatPrice(detail.customer.totalSpent)} spent)
                </span>
              </h3>
              {detail.recentOrders.length === 0 ? (
                <p className="small text-muted">This customer has not placed an order yet.</p>
              ) : (
                <table className="table table-sm small mb-1">
                  <thead>
                    <tr>
                      <th>Order</th>
                      <th>Date</th>
                      <th className="text-end">Total</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.recentOrders.map((o) => (
                      <tr key={o.orderId}>
                        <td className="fw-semibold">{o.orderNumber}</td>
                        <td className="text-nowrap">{formatDate(o.createdAt)}</td>
                        <td className="text-end text-nowrap">{formatPrice(o.totalAmount)}</td>
                        <td>
                          <StatusBadge status={o.status} label={o.status === 'PLACED' ? 'Pending' : undefined} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {detail.customer.orderCount > detail.recentOrders.length && (
                <p className="small text-muted mb-0">Showing the latest {detail.recentOrders.length} orders.</p>
              )}
              {detail.customer.orderCount > 0 && (
                <Link to="/admin/orders" className="small" onClick={() => setDetail(null)}>
                  Open Order management →
                </Link>
              )}
            </div>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={Boolean(toggleTarget)}
        title={toggleTarget?.active ? `Deactivate ${toggleTarget?.fullName}?` : `Activate ${toggleTarget?.fullName}?`}
        confirmLabel={toggleTarget?.active ? 'Deactivate' : 'Activate'}
        danger={toggleTarget?.active}
        busy={saving}
        onConfirm={toggle}
        onCancel={() => setToggleTarget(null)}
      >
        {toggleTarget?.active ? (
          <Alert variant="warning" className="mb-0">
            <strong>Warning:</strong> the customer is signed out and cannot sign in or place orders until the account is activated again.
            This is a soft delete: their profile, addresses and order history stay in the database.
          </Alert>
        ) : (
          `${toggleTarget?.fullName} will be able to sign in and shop again.`
        )}
      </ConfirmDialog>
    </>
  )
}
