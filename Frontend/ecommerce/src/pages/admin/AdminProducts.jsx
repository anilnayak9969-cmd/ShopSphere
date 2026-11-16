import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import adminService from '../../services/adminService'
import categoryService from '../../services/categoryService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import Modal, { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field } from '../../components/FormControls'
import { ActiveBadge, Alert, EmptyState, ErrorState, PageHeader, PageLoader, PriceTag, SafeImage, Segment, StatCard, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate, formatPrice } from '../../utils/format'

/**
 * Product Management module — the admin Product dashboard.
 * View every product with its stock, price and status, add new products, edit them, and
 * deactivate (soft delete) or reactivate them. The approval queue for seller listings is a tab here too.
 */
const APPROVAL_TABS = [
  { value: '', label: 'All products' },
  { value: 'PENDING', label: 'Approval queue' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
]

const STATUS_TABS = [
  { value: '', label: 'All' },
  { value: 'true', label: 'Active' },
  { value: 'false', label: 'Inactive' },
]

export default function AdminProducts() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const rawApproval = params.get('approval')
  const approval = ['PENDING', 'APPROVED', 'REJECTED'].includes(rawApproval) ? rawApproval : ''
  const rawStatus = params.get('status')
  const status = rawStatus === 'active' ? 'true' : rawStatus === 'inactive' ? 'false' : ''
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  // ?categoryId= comes from the Categories page ("Review N awaiting approval").
  const [categoryId, setCategoryId] = useState(() => params.get('categoryId') || '')
  const [categories, setCategories] = useState([])
  const [summary, setSummary] = useState(null)
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => adminService.getProducts({ search: debouncedSearch, categoryId, approval, active: status, page, size: 10 }),
    [debouncedSearch, categoryId, approval, status],
  )

  const [toggleTarget, setToggleTarget] = useState(null)
  const [review, setReview] = useState(null) // product being reviewed
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reviewError, setReviewError] = useState(null)
  const [busy, setBusy] = useState(false)

  const loadSummary = useCallback(() => {
    adminService.getProductSummary().then(setSummary).catch(() => setSummary(null))
  }, [])

  useEffect(() => {
    categoryService.getCategories().then(setCategories).catch(() => setCategories([]))
    loadSummary()
  }, [loadSummary])

  const updateParams = (changes) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)))
    setParams(next, { replace: true })
  }

  const replaceOrDrop = (updated) =>
    setResult((r) => ({
      ...r,
      // In a filtered view, a product that no longer matches the filter leaves the list.
      content:
        (approval && updated.approvalStatus !== approval) || (status && String(updated.active) !== status)
          ? r.content.filter((p) => p.productId !== updated.productId)
          : r.content.map((p) => (p.productId === updated.productId ? updated : p)),
    }))

  const toggle = async () => {
    setBusy(true)
    try {
      const updated = await adminService.setProductActive(toggleTarget.productId, !toggleTarget.active)
      replaceOrDrop(updated)
      loadSummary()
      toast.success(updated.active ? `"${updated.productName}" is active again.` : `"${updated.productName}" has been deactivated.`)
      setToggleTarget(null)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const openReview = (product) => {
    setReview(product)
    setRejecting(false)
    setReason('')
    setReviewError(null)
  }

  const decide = async (approve) => {
    if (!approve && !reason.trim()) {
      setReviewError('Give the seller a reason so they can fix the listing.')
      return
    }
    setBusy(true)
    setReviewError(null)
    try {
      const updated = approve
        ? await adminService.approveProduct(review.productId)
        : await adminService.rejectProduct(review.productId, reason.trim())
      replaceOrDrop(updated)
      loadSummary()
      toast.success(`${updated.productName} ${approve ? 'approved and visible in the store' : 'rejected'}.`)
      setReview(null)
    } catch (err) {
      setReviewError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const lowStock = summary?.lowStockThreshold ?? 5

  return (
    <>
      <PageHeader
        eyebrow="Catalog"
        title="Product management"
        subtitle="Add, update and deactivate products, keep an eye on stock, and approve new seller listings."
        actions={
          <Link to="/admin/products/new" className="btn btn-ink">
            <i className="bi bi-plus-lg me-2" />
            New product
          </Link>
        }
      />

      <div className="row g-3 mb-4">
        <div className="col-6 col-lg-3">
          <StatCard label="Total products" value={summary?.total ?? '—'} icon="bi-box-seam" foot="Active and inactive" />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Active" value={summary?.active ?? '—'} icon="bi-check2-circle" foot="Switched on for the store" />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard label="Inactive" value={summary?.inactive ?? '—'} icon="bi-archive" foot="Soft deleted, hidden from customers" />
        </div>
        <div className="col-6 col-lg-3">
          <StatCard
            label="Low stock"
            value={summary?.lowStock ?? '—'}
            icon="bi-exclamation-triangle"
            foot={`${lowStock} units or fewer · ${summary?.awaitingApproval ?? 0} awaiting approval`}
          />
        </div>
      </div>

      <div className="d-flex flex-wrap gap-2 justify-content-between mb-3">
        <Segment label="Approval status" options={APPROVAL_TABS} value={approval} onChange={(value) => updateParams({ approval: value })} />
        <Segment
          label="Product status"
          options={STATUS_TABS}
          value={status}
          onChange={(value) => updateParams({ status: value === 'true' ? 'active' : value === 'false' ? 'inactive' : '' })}
        />
      </div>

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Search name, brand, SKU or category"
              aria-label="Search products"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <select
            className="form-select form-select-sm ms-auto"
            style={{ maxWidth: 220 }}
            aria-label="Filter by category"
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c.categoryId} value={c.categoryId}>
                {c.categoryName}
              </option>
            ))}
          </select>
        </div>

        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon={approval === 'PENDING' ? 'bi-check2-all' : 'bi-box-seam'} title={approval === 'PENDING' ? 'Queue is empty' : 'No products found'}>
            {approval === 'PENDING' ? 'New products and edited listings waiting for review appear here.' : 'No products match these filters.'}
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
                  <th>Last updated</th>
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
                          {p.approvalStatus === 'APPROVED' && p.active ? (
                            <Link to={`/products/${p.productId}`} className="ss-cell-title text-decoration-none">
                              {p.productName}
                            </Link>
                          ) : (
                            <div className="ss-cell-title">{p.productName}</div>
                          )}
                          <div className="ss-cell-sub">
                            SKU {p.sku} · {p.storeName}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>
                      {p.categoryName}
                      {!p.categoryActive && <div className="small text-danger">category inactive</div>}
                    </td>
                    <td className="text-nowrap">
                      {formatPrice(p.sellingPrice)}
                      {p.discountPercent > 0 && <div className="ss-cell-sub">MRP {formatPrice(p.price)}</div>}
                    </td>
                    <td className={p.stockQuantity <= lowStock ? 'text-danger fw-bold' : ''}>
                      {p.stockQuantity}
                      {p.stockQuantity === 0 ? <div className="small">out of stock</div> : p.stockQuantity <= lowStock && <div className="small">low stock</div>}
                    </td>
                    <td>
                      <div className="d-flex flex-column align-items-start gap-1">
                        <ActiveBadge active={p.active} />
                        {p.approvalStatus !== 'APPROVED' && (
                          <StatusBadge status={p.approvalStatus} label={p.approvalStatus === 'PENDING' ? 'Awaiting review' : undefined} />
                        )}
                      </div>
                      {p.approvalStatus === 'REJECTED' && p.rejectionReason && (
                        <div className="ss-cell-sub mt-1" style={{ maxWidth: 200 }}>
                          {p.rejectionReason}
                        </div>
                      )}
                    </td>
                    <td className="text-nowrap">{formatDate(p.updatedAt)}</td>
                    <td className="text-end text-nowrap">
                      {p.approvalStatus !== 'APPROVED' && (
                        <button type="button" className="btn btn-sm btn-ink me-1" onClick={() => openReview(p)}>
                          Review
                        </button>
                      )}
                      <Link to={`/admin/products/${p.productId}/edit`} className="btn btn-sm btn-outline-ink me-1">
                        <i className="bi bi-pencil me-1" aria-hidden="true" />
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
        title={toggleTarget?.active ? `Deactivate “${toggleTarget?.productName}”?` : `Activate “${toggleTarget?.productName}”?`}
        confirmLabel={toggleTarget?.active ? 'Deactivate' : 'Activate'}
        danger={toggleTarget?.active}
        busy={busy}
        onConfirm={toggle}
        onCancel={() => setToggleTarget(null)}
      >
        {toggleTarget?.active ? (
          <Alert variant="warning" className="mb-0">
            <strong>Warning:</strong> deactivating this product will <strong>hide it from the customer interface</strong>. Customers can no longer find or buy it.
            This is a soft delete: the product stays in the database with status <em>inactive</em>, so past orders keep it, and you can activate it again at any time.
          </Alert>
        ) : (
          `${toggleTarget?.productName} will be visible to customers again (if it is approved and its category is active).`
        )}
      </ConfirmDialog>

      <Modal
        open={Boolean(review)}
        size="lg"
        title="Review listing"
        subtitle={review && `${review.storeName} · SKU ${review.sku}`}
        onClose={() => !busy && setReview(null)}
        footer={
          rejecting ? (
            <>
              <button type="button" className="btn btn-ghost" onClick={() => setRejecting(false)} disabled={busy}>
                Back
              </button>
              <button type="button" className="btn btn-danger-soft" onClick={() => decide(false)} disabled={busy}>
                {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                Reject listing
              </button>
            </>
          ) : (
            <>
              <button type="button" className="btn btn-danger-soft" onClick={() => setRejecting(true)} disabled={busy}>
                Reject…
              </button>
              <button type="button" className="btn btn-ink" onClick={() => decide(true)} disabled={busy}>
                {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                Approve
              </button>
            </>
          )
        }
      >
        {review && (
          <div className="d-grid gap-3">
            {reviewError && <Alert variant="danger">{reviewError}</Alert>}
            <div className="d-flex gap-3 flex-wrap">
              <div className="ss-thumb" style={{ width: 120, height: 120 }}>
                <SafeImage src={review.imageUrl} alt={review.productName} />
              </div>
              <div className="min-w-0 flex-grow-1">
                <h3 className="h5 mb-1">{review.productName}</h3>
                <div className="small text-muted mb-2">
                  {review.brand ? `${review.brand} · ` : ''}
                  {review.categoryName} · {review.stockQuantity} in stock
                </div>
                <PriceTag price={review.price} sellingPrice={review.sellingPrice} discountPercent={review.discountPercent} />
              </div>
            </div>
            <div>
              <div className="form-label">Description</div>
              <p className="small mb-0" style={{ whiteSpace: 'pre-line' }}>
                {review.description}
              </p>
            </div>
            {rejecting && (
              <Field
                id="reject-reason"
                as="textarea"
                rows={3}
                maxLength={255}
                label="Reason shown to the seller"
                placeholder="e.g. The main photo is blurry, please upload a clear product image."
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            )}
          </div>
        )}
      </Modal>
    </>
  )
}
