import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import sellerService from '../../services/sellerService'
import { useToast } from '../../context/ToastContext'
import useDebounce from '../../hooks/useDebounce'
import usePagedList from '../../hooks/usePagedList'
import Pagination from '../../components/Pagination'
import { EmptyState, ErrorState, PageHeader, PageLoader, SafeImage, Segment } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'

/** One editable stock cell. Saves on Enter or the save button. */
function StockEditor({ product, onSaved }) {
  const toast = useToast()
  const [value, setValue] = useState(String(product.stockQuantity))
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)
  const dirty = value !== String(product.stockQuantity)

  const save = async (event) => {
    event.preventDefault()
    const n = Number(value)
    if (value === '' || !Number.isInteger(n) || n < 0 || n > 100000) {
      setError('0 to 100000')
      return
    }
    setSaving(true)
    setError(null)
    try {
      const updated = await sellerService.updateStock(product.productId, n)
      onSaved(updated)
      toast.success(`Stock for ${updated.productName} set to ${updated.stockQuantity}.`)
    } catch (err) {
      setError(getFieldErrors(err).stockQuantity || getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={save} className="d-flex gap-2 align-items-start" noValidate>
      <div>
        <input
          type="number"
          min="0"
          max="100000"
          className={`form-control form-control-sm ${error ? 'is-invalid' : ''}`}
          style={{ width: 100 }}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          aria-label={`Stock for ${product.productName}`}
        />
        {error && <div className="invalid-feedback d-block">{error}</div>}
      </div>
      <button type="submit" className="btn btn-sm btn-ink" disabled={!dirty || saving}>
        {saving ? <span className="spinner-border spinner-border-sm" aria-label="Saving" /> : 'Save'}
      </button>
    </form>
  )
}

export default function SellerInventory() {
  const [threshold, setThreshold] = useState(null)
  const [view, setView] = useState('all')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounce(search.trim(), 300)

  // The low-stock threshold is a platform setting; the dashboard reports it.
  useEffect(() => {
    sellerService
      .getDashboard()
      .then((d) => setThreshold(d.lowStockThreshold))
      .catch(() => setThreshold(5))
  }, [])

  const maxStock = view === 'low' ? threshold : view === 'out' ? 0 : undefined
  const { result, error, reload, setPage, setResult } = usePagedList(
    (page) => sellerService.getProducts({ search: debouncedSearch, maxStock, page, size: 15 }),
    [debouncedSearch, view, threshold],
  )

  const replace = (updated) =>
    setResult((r) => ({ ...r, content: r.content.map((p) => (p.productId === updated.productId ? updated : p)) }))

  return (
    <>
      <PageHeader eyebrow="Catalog" title="Inventory" subtitle="Keep stock accurate so customers never order something you cannot ship. Stock changes go live immediately." />

      <div className="ss-card overflow-hidden">
        <div className="ss-toolbar">
          <div className="ss-toolbar-search">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              type="search"
              className="form-control form-control-sm"
              placeholder="Search name, brand or SKU"
              aria-label="Search inventory"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <Segment
            label="Stock filter"
            value={view}
            onChange={setView}
            options={[
              { value: 'all', label: 'All products' },
              { value: 'low', label: threshold == null ? 'Low stock' : `Low stock (≤ ${threshold})` },
              { value: 'out', label: 'Out of stock' },
            ]}
          />
        </div>

        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !result || threshold == null ? (
          <PageLoader />
        ) : result.content.length === 0 ? (
          <EmptyState icon="bi-boxes" title={view === 'all' ? 'No products' : 'Nothing here'}>
            {view === 'all' ? 'Products you add will appear here.' : 'Great — no products match this stock filter.'}
          </EmptyState>
        ) : (
          <div className="table-responsive">
            <table className="table ss-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>SKU</th>
                  <th>Sold</th>
                  <th>Status</th>
                  <th>Units in stock</th>
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
                        <Link to={`/seller/products/${p.productId}/edit`} className="ss-cell-title text-decoration-none">
                          {p.productName}
                        </Link>
                      </div>
                    </td>
                    <td className="small text-muted">{p.sku}</td>
                    <td>{p.soldCount}</td>
                    <td>
                      {p.stockQuantity === 0 ? (
                        <span className="ss-pill ss-pill-danger">Out of stock</span>
                      ) : p.stockQuantity <= threshold ? (
                        <span className="ss-pill ss-pill-warning">Low stock</span>
                      ) : (
                        <span className="ss-pill ss-pill-success">In stock</span>
                      )}
                    </td>
                    <td>
                      <StockEditor key={`${p.productId}-${p.stockQuantity}`} product={p} onSaved={replace} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}
    </>
  )
}
