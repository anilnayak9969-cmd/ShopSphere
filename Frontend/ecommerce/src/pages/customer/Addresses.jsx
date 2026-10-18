import { useCallback, useEffect, useState } from 'react'
import accountService from '../../services/accountService'
import { useToast } from '../../context/ToastContext'
import AddressForm, { EMPTY_ADDRESS, formatAddress, validateAddress } from '../../components/AddressForm'
import Modal, { ConfirmDialog } from '../../components/Modal'
import { EmptyState, ErrorState, PageLoader } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'

export default function Addresses() {
  const toast = useToast()
  const [addresses, setAddresses] = useState(null)
  const [error, setError] = useState(null)
  const [editor, setEditor] = useState(null) // null | { id?: number }
  const [form, setForm] = useState({ ...EMPTY_ADDRESS, defaultAddress: false })
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(null)

  const load = useCallback(() => {
    setError(null)
    accountService
      .getAddresses()
      .then(setAddresses)
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const openNew = () => {
    setForm({ ...EMPTY_ADDRESS, defaultAddress: addresses?.length === 0 })
    setErrors({})
    setEditor({})
  }

  const openEdit = (a) => {
    setForm({ ...EMPTY_ADDRESS, ...a, label: a.label || '', addressLine2: a.addressLine2 || '' })
    setErrors({})
    setEditor({ id: a.addressId })
  }

  const save = async (event) => {
    event.preventDefault()
    const found = validateAddress(form)
    setErrors(found)
    if (Object.keys(found).length) return
    setSaving(true)
    try {
      const payload = { ...form }
      delete payload.addressId
      if (editor.id) await accountService.updateAddress(editor.id, payload)
      else await accountService.createAddress(payload)
      toast.success(editor.id ? 'Address updated.' : 'Address saved.')
      setEditor(null)
      load()
    } catch (err) {
      setErrors(getFieldErrors(err))
      toast.error(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const makeDefault = async (a) => {
    try {
      await accountService.makeDefaultAddress(a.addressId)
      toast.success('Default address updated.')
      load()
    } catch (err) {
      toast.error(getErrorMessage(err))
    }
  }

  const confirmDelete = async () => {
    setSaving(true)
    try {
      await accountService.deleteAddress(deleting.addressId)
      toast.info('Address deleted.')
      setDeleting(null)
      load()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="d-flex justify-content-between align-items-end flex-wrap gap-2 mb-4">
        <div>
          <span className="ss-eyebrow">Delivery</span>
          <h1 className="mt-1 mb-0" style={{ fontSize: 'clamp(2.2rem, 4vw, 3rem)' }}>
            Addresses
          </h1>
        </div>
        <button type="button" className="btn btn-ink" onClick={openNew} disabled={addresses?.length >= 10}>
          <i className="bi bi-plus-lg me-2" />
          Add address
        </button>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : !addresses ? (
        <PageLoader />
      ) : addresses.length === 0 ? (
        <div className="ss-card">
          <EmptyState icon="bi-geo-alt" title="No saved addresses" action={<button type="button" className="btn btn-ink" onClick={openNew}>Add your first address</button>}>
            Save addresses here to check out faster.
          </EmptyState>
        </div>
      ) : (
        <div className="row g-3">
          {addresses.map((a) => (
            <div key={a.addressId} className="col-md-6">
              <div className={`ss-card ss-card-pad h-100 ${a.defaultAddress ? 'ss-address-default' : ''}`}>
                <div className="d-flex justify-content-between align-items-start gap-2 mb-2">
                  <div className="fw-bold">
                    {a.fullName}
                    {a.label && <span className="ss-pill ss-pill-muted ms-2">{a.label}</span>}
                  </div>
                  {a.defaultAddress && <span className="ss-pill ss-pill-gold">Default</span>}
                </div>
                <p className="small text-muted mb-1">{formatAddress(a)}</p>
                <p className="small text-muted">Phone: {a.phone}</p>
                <div className="d-flex gap-2 flex-wrap">
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => openEdit(a)}>
                    Edit
                  </button>
                  {!a.defaultAddress && (
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => makeDefault(a)}>
                      Set as default
                    </button>
                  )}
                  <button type="button" className="btn btn-danger-soft btn-sm" onClick={() => setDeleting(a)}>
                    Delete
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={Boolean(editor)}
        size="lg"
        title={editor?.id ? 'Edit address' : 'New address'}
        onClose={() => !saving && setEditor(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setEditor(null)} disabled={saving}>
              Cancel
            </button>
            <button type="submit" form="address-form" className="btn btn-ink" disabled={saving}>
              {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
              Save address
            </button>
          </>
        }
      >
        <form id="address-form" onSubmit={save} noValidate>
          <AddressForm value={form} onChange={setForm} errors={errors} />
          <div className="form-check mt-3">
            <input
              id="address-default"
              type="checkbox"
              className="form-check-input"
              checked={form.defaultAddress}
              onChange={(e) => setForm((f) => ({ ...f, defaultAddress: e.target.checked }))}
            />
            <label htmlFor="address-default" className="form-check-label">
              Use as my default delivery address
            </label>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete this address?"
        confirmLabel="Delete"
        danger
        busy={saving}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(null)}
      >
        Past orders keep their own copy of the address, so they are not affected.
      </ConfirmDialog>
    </>
  )
}
