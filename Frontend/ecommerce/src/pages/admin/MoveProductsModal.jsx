import { useEffect, useState } from 'react'
import adminService from '../../services/adminService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import Modal from '../../components/Modal'
import { Alert, SafeImage, StatusBadge } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatPrice } from '../../utils/format'

/**
 * Admin picks existing products and moves them into `category` (e.g. filling a new "Comics" category).
 * A product that is already approved stays approved, so the category goes live in the store straight away.
 */
export default function MoveProductsModal({ category, onClose, onMoved }) {
  const toast = useToast()
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)
  const [products, setProducts] = useState(null)
  const [error, setError] = useState(null)
  const [movingId, setMovingId] = useState(null)
  const [moved, setMoved] = useState([]) // product ids moved in this session

  useEffect(() => {
    if (!category) return
    let cancelled = false
    setError(null)
    adminService
      .getProducts({ search: debouncedSearch, size: 8 })
      .then((page) => !cancelled && setProducts(page.content))
      .catch((err) => !cancelled && setError(getErrorMessage(err)))
    return () => {
      cancelled = true
    }
  }, [category, debouncedSearch])

  const close = () => {
    if (movingId) return
    setSearch('')
    setProducts(null)
    setMoved([])
    onClose(moved.length > 0)
  }

  const move = async (product) => {
    setMovingId(product.productId)
    setError(null)
    try {
      const updated = await adminService.moveProduct(product.productId, category.categoryId)
      setProducts((list) => list.map((p) => (p.productId === updated.productId ? updated : p)))
      setMoved((ids) => [...ids, updated.productId])
      toast.success(
        updated.approvalStatus === 'APPROVED' && updated.active
          ? `“${updated.productName}” is now live in ${category.categoryName}.`
          : `“${updated.productName}” moved to ${category.categoryName}. Approve it to make it live.`,
      )
      onMoved?.()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setMovingId(null)
    }
  }

  return (
    <Modal
      open={Boolean(category)}
      size="lg"
      title={`Add products to “${category?.categoryName}”`}
      subtitle="Move existing products into this category. Approved products show up for customers straight away."
      onClose={close}
      footer={
        <button type="button" className="btn btn-ink" onClick={close} disabled={Boolean(movingId)}>
          Done{moved.length > 0 ? ` (${moved.length} moved)` : ''}
        </button>
      }
    >
      <div className="position-relative mb-3">
        <i
          className="bi bi-search position-absolute top-50 translate-middle-y text-muted"
          style={{ left: '0.85rem' }}
          aria-hidden="true"
        />
        <input
          type="search"
          className="form-control"
          style={{ paddingLeft: '2.3rem' }}
          placeholder="Search products by name, brand or SKU"
          aria-label="Search products"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>
      {error && (
        <Alert variant="danger" className="mb-3">
          {error}
        </Alert>
      )}
      {!products ? (
        <p className="text-muted small mb-0">Loading products…</p>
      ) : products.length === 0 ? (
        <p className="text-muted small mb-0">No products match “{search}”.</p>
      ) : (
        <ul className="list-unstyled d-flex flex-column gap-2 mb-0">
          {products.map((p) => {
            const here = p.categoryId === category.categoryId
            return (
              <li key={p.productId} className="d-flex flex-wrap flex-sm-nowrap align-items-center gap-2 gap-sm-3 border rounded-3 p-2 min-w-0">
                <div className="ss-thumb">
                  <SafeImage src={p.imageUrl} alt="" />
                </div>
                <div className="min-w-0 flex-grow-1" style={{ flexBasis: 0 }}>
                  <div className="ss-cell-title text-truncate" title={p.productName}>{p.productName}</div>
                  <div className="ss-cell-sub text-truncate">
                    {p.storeName} · {formatPrice(p.sellingPrice)} · in <strong>{p.categoryName}</strong>
                  </div>
                </div>
                <StatusBadge status={p.approvalStatus} label={p.approvalStatus === 'PENDING' ? 'Awaiting review' : undefined} />
                {here ? (
                  <span className="text-success small fw-semibold text-nowrap">
                    <i className="bi bi-check-circle-fill me-1" aria-hidden="true" />
                    In this category
                  </span>
                ) : (
                  <button
                    type="button"
                    className="btn btn-outline-ink btn-sm text-nowrap"
                    onClick={() => move(p)}
                    disabled={Boolean(movingId)}
                  >
                    {movingId === p.productId && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                    Move here
                  </button>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </Modal>
  )
}
