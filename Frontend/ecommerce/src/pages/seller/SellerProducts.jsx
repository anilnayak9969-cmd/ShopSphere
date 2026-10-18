import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import sellerService from '../../services/sellerService'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import Modal, { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { ActiveBadge, EmptyState, ErrorState, PageHeader, PageLoader, SafeImage, Segment, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatPrice } from '../../utils/format'
import SellerStatusBanner from './SellerStatusBanner'

const APPROVAL_FILTERS = [
  { value: '', label: 'All' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'PENDING', label: 'Awaiting approval' },
  { value: 'REJECTED', label: 'Rejected' },
]

export default function SellerProducts() {
  const [params, setParams] = useSearchParams()
  const approval = APPROVAL_FILTERS.some((f) => f.value === params.get('approval')) ? params.get('approval') : ''
  const { user } = useAuth()
  const toast = useToast()
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [toggleTarget, setToggleTarget] = useState(null)
  const [stockTarget, setStockTarget] = useState(null)
  const [stockValue, setStockValue] = useState('')
  const [stockError, setStockError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setError(null)
    sellerService
      .getProducts({ search: debouncedSearch, approval, page, size: 10 })
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
  }, [debouncedSearch, approval, page])

  useEffect(() => {
    load()
  }, [load])

  const replace = (updated) =>
    setResult((r) => ({ ...r, content: r.content.map((p) => (p.productId === updated.productId ? updated : p)) }))

  const toggleActive = async () => {
    setBusy(true)
    try {
      const updated = await sellerService.setProductActive(toggleTarget.productId, !toggleTarget.active)
      replace(updated)
      toast.success(`${updated.productName} is now ${updated.active ? 'live in the store' : 'hidden from customers'}.`)
      setToggleTarget(null)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const openStock = (product) => {
    setStockTarget(product)
    setStockValue(String(product.stockQuantity))
    setStockError(null)
  }

  const saveStock = async (event) => {
    event.preventDefault()
    const value = Number(stockValue)
    if (!Number.isInteger(value) || value < 0 || value > 100000) {
      setStockError('Enter a whole number between 0 and 100000')
      return
    }
    setBusy(true)
    try {
      replace(await sellerService.updateStock(stockTarget.productId, value))
      toast.success('Stock updated.')
      setStockTarget(null)
    } catch (err) {
      setStockError(getFieldErrors(err).stockQuantity || getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Catalog"
        title="Your products"
        subtitle="New products and changes to name, description, images or category are reviewed by the ShopSphere team before customers see them. Price and stock changes go live immediately."
        actions={
          <Link to="/seller/products/new" className={`btn btn-ink ${user.sellerStatus !== 'APPROVED' ? 'disabled' : ''}`}>
            <i className="bi bi-plus-lg me-2" />
            Add product
          </Link>
        }
      />
      <SellerStatusBanner status={user.sellerStatus} />

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Search name, brand or SKU"
              aria-label="Search your products"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
            />
          </div>
          <Segment
            label="Approval status"
            options={APPROVAL_FILTERS}
            value={approval}
            onChange={(value) => {
              setParams(value ? { approval: value } : {}, { replace: true })
              setPage(0)
            }}
          />
        </div>

        {error ? (
          <ErrorState message={error} onRetry={load} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-box-seam" title={debouncedSearch || approval ? 'No matching products' : 'No products yet'}>
            {debouncedSearch || approval ? 'Try a different search or filter.' : 'Add your first product to start selling.'}
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Category</th>
                  <th>Price</th>
                  <th>Stock</th>
                  <th>Status</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((p) => (
                  <tr key={p.productId}>
                    <td>
                      <div className="d-flex align-items-center gap-3">
                        <div className="ss-thumb">
                          <SafeImage src={p.imageUrl} alt="" />
                        </div>
                        <div className="min-w-0">
                          <div className="ss-cell-title">{p.productName}</div>
                          <div className="ss-cell-sub">SKU {p.sku}</div>
                        </div>
                      </div>
                    </td>
                    <td>
                      {p.categoryName}
                      {!p.categoryActive && <div className="small text-danger">Category inactive</div>}
                    </td>
                    <td>
                      <div className="fw-semibold">{formatPrice(p.sellingPrice)}</div>
                      {p.discountPercent > 0 && <div className="ss-cell-sub">{p.discountPercent}% off {formatPrice(p.price)}</div>}
                    </td>
                    <td>
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => openStock(p)}>
                        <span className={p.stockQuantity <= 5 ? 'text-danger fw-bold' : ''}>{p.stockQuantity}</span>
                        <i className="bi bi-pencil ms-2 small" aria-hidden="true" />
                        <span className="visually-hidden">Edit stock</span>
                      </button>
                    </td>
                    <td>
                      <div className="d-flex flex-column align-items-start gap-1">
                        <StatusBadge status={p.approvalStatus} label={p.approvalStatus === 'PENDING' ? 'Awaiting approval' : undefined} />
                        <ActiveBadge active={p.active} on="Visible" off="Hidden" />
                      </div>
                      {p.approvalStatus === 'REJECTED' && p.rejectionReason && (
                        <div className="small text-danger mt-1" style={{ maxWidth: 220 }}>
                          {p.rejectionReason}
                        </div>
                      )}
                    </td>
                    <td className="text-end text-nowrap">
                      <Link to={`/seller/products/${p.productId}/edit`} className="btn btn-ghost btn-sm me-2">
                        Edit
                      </Link>
                      <button
                        type="button"
                        className={`btn btn-sm ${p.active ? 'btn-danger-soft' : 'btn-outline-ink'}`}
                        onClick={() => setToggleTarget(p)}
                      >
                        {p.active ? 'Deactivate' : 'Activate'}
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

      <ConfirmDialog
        open={Boolean(toggleTarget)}
        title={toggleTarget?.active ? 'Hide this product?' : 'Publish this product?'}
        confirmLabel={toggleTarget?.active ? 'Deactivate' : 'Activate'}
        danger={toggleTarget?.active}
        busy={busy}
        onConfirm={toggleActive}
        onCancel={() => setToggleTarget(null)}
      >
        {toggleTarget?.active
          ? `${toggleTarget?.productName} will disappear from the store. Past orders are not affected.`
          : `${toggleTarget?.productName} will become visible to customers.`}
      </ConfirmDialog>

      <Modal
        open={Boolean(stockTarget)}
        title="Update stock"
        subtitle={stockTarget?.productName}
        onClose={() => !busy && setStockTarget(null)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setStockTarget(null)} disabled={busy}>
              Cancel
            </button>
            <button type="submit" form="stock-form" className="btn btn-ink" disabled={busy}>
              Save stock
            </button>
          </>
        }
      >
        <form id="stock-form" onSubmit={saveStock} noValidate>
          <label htmlFor="stock-input" className="form-label">
            Units available
          </label>
          <input
            id="stock-input"
            type="number"
            min="0"
            className={`form-control ${stockError ? 'is-invalid' : ''}`}
            value={stockValue}
            onChange={(e) => setStockValue(e.target.value)}
          />
          {stockError && <div className="invalid-feedback d-block">{stockError}</div>}
        </form>
      </Modal>
    </>
  )
}
