import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import adminService from '../../services/adminService'
import categoryService from '../../services/categoryService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import Modal, { ConfirmDialog } from '../../components/Modal'
import Pagination from '../../components/Pagination'
import { Field } from '../../components/FormControls'
import { ActiveBadge, Alert, EmptyState, ErrorState, PageHeader, PageLoader, PriceTag, SafeImage, Segment, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDateTime, formatPrice } from '../../utils/format'

const TABS = [
  { value: 'PENDING', label: 'Approval queue' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: '', label: 'All products' },
]

export default function AdminProducts() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  // ?approval=ALL shows every product; no parameter opens the approval queue.
  const raw = params.get('approval')
  const approval = raw === 'ALL' ? '' : raw === 'APPROVED' || raw === 'REJECTED' ? raw : 'PENDING'
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const [categoryId, setCategoryId] = useState('')
  const [categories, setCategories] = useState([])
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => adminService.getProducts({ search: debouncedSearch, categoryId, approval, page, size: 10 }),
    [debouncedSearch, categoryId, approval],
  )

  const [toggleTarget, setToggleTarget] = useState(null)
  const [review, setReview] = useState(null) // product being reviewed
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reviewError, setReviewError] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    categoryService.getCategories().then(setCategories).catch(() => setCategories([]))
  }, [])

  const replaceOrDrop = (updated) =>
    setResult((r) => ({
      ...r,
      // In a filtered tab, a product that changed approval state leaves the list.
      content: approval && updated.approvalStatus !== approval
        ? r.content.filter((p) => p.productId !== updated.productId)
        : r.content.map((p) => (p.productId === updated.productId ? updated : p)),
    }))

  const toggle = async () => {
    setBusy(true)
    try {
      const updated = await adminService.setProductActive(toggleTarget.productId, !toggleTarget.active)
      replaceOrDrop(updated)
      toast.success(`${updated.productName} ${updated.active ? 'restored' : 'taken down'}.`)
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
      toast.success(`${updated.productName} ${approve ? 'approved and visible in the store' : 'rejected'}.`)
      setReview(null)
    } catch (err) {
      setReviewError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader eyebrow="Catalog" title="Products" subtitle="Approve new listings and edits, and take down anything that breaks marketplace policy." />
      <div className="mb-3">
        <Segment
          label="Approval status"
          options={TABS}
          value={approval}
          onChange={(value) => setParams(value === 'PENDING' ? {} : { approval: value || 'ALL' }, { replace: true })}
        />
      </div>
      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Search name, brand or SKU"
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
                  <th>Seller</th>
                  <th>Category</th>
                  <th>Price</th>
                  <th>Stock</th>
                  <th>Status</th>
                  <th className="text-end">Action</th>
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
                            SKU {p.sku} · updated {formatDateTime(p.updatedAt)}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>{p.storeName}</td>
                    <td>
                      {p.categoryName}
                      {!p.categoryActive && <div className="small text-danger">inactive</div>}
                    </td>
                    <td>{formatPrice(p.sellingPrice)}</td>
                    <td className={p.stockQuantity <= 5 ? 'text-danger fw-bold' : ''}>{p.stockQuantity}</td>
                    <td>
                      <div className="d-flex flex-column align-items-start gap-1">
                        <StatusBadge status={p.approvalStatus} label={p.approvalStatus === 'PENDING' ? 'Awaiting review' : undefined} />
                        <ActiveBadge active={p.active} on="Seller: visible" off="Hidden" />
                      </div>
                      {p.approvalStatus === 'REJECTED' && p.rejectionReason && (
                        <div className="ss-cell-sub mt-1" style={{ maxWidth: 200 }}>
                          {p.rejectionReason}
                        </div>
                      )}
                    </td>
                    <td className="text-end text-nowrap">
                      {p.approvalStatus !== 'APPROVED' && (
                        <button type="button" className="btn btn-sm btn-ink me-1" onClick={() => openReview(p)}>
                          Review
                        </button>
                      )}
                      {p.approvalStatus === 'APPROVED' && (
                        <button
                          type="button"
                          className={`btn btn-sm ${p.active ? 'btn-danger-soft' : 'btn-outline-ink'}`}
                          onClick={() => setToggleTarget(p)}
                        >
                          {p.active ? 'Take down' : 'Restore'}
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

      <ConfirmDialog
        open={Boolean(toggleTarget)}
        title={toggleTarget?.active ? 'Take down this product?' : 'Restore this product?'}
        confirmLabel={toggleTarget?.active ? 'Take down' : 'Restore'}
        danger={toggleTarget?.active}
        busy={busy}
        onConfirm={toggle}
        onCancel={() => setToggleTarget(null)}
      >
        {toggleTarget?.active
          ? `${toggleTarget?.productName} by ${toggleTarget?.storeName} will be hidden from customers.`
          : `${toggleTarget?.productName} will be visible to customers again.`}
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
