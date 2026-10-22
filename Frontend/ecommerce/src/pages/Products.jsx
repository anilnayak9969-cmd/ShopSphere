import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import categoryService, { toCategoryTree } from '../services/categoryService'
import productService from '../services/productService'
import ProductCard from '../components/ProductCard'
import CategoryCard from '../components/CategoryCard'
import Pagination from '../components/Pagination'
import { EmptyState, ErrorState, ProductGridSkeleton, SafeImage } from '../components/ui'
import { getErrorMessage } from '../utils/errors'
import { formatPrice } from '../utils/format'

const SORTS = [
  { value: 'popular', label: 'Popularity' },
  { value: 'newest', label: 'Newest first' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'rating', label: 'Customer rating' },
  { value: 'discount', label: 'Biggest discount' },
]
const RATINGS = [4, 3]
const DISCOUNTS = [10, 25, 50]

/**
 * Every filter lives in the URL (?search=&categoryId=&brand=&minPrice=...). Results are shareable,
 * survive a refresh, keep the back button working — and the backend does the actual filtering.
 */
export default function Products() {
  const [params, setParams] = useSearchParams()
  const [categories, setCategories] = useState([])
  const [brands, setBrands] = useState([])
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [filtersOpen, setFiltersOpen] = useState(false)

  const search = params.get('search') || ''
  const categoryId = params.get('categoryId') || ''
  const selectedBrands = params.getAll('brand')
  const minPrice = params.get('minPrice') || ''
  const maxPrice = params.get('maxPrice') || ''
  const inStock = params.get('inStock') === 'true'
  const minRating = params.get('minRating') || ''
  const minDiscount = params.get('minDiscount') || ''
  const sort = params.get('sort') || 'popular'
  const page = Number(params.get('page') || 0)
  const brandKey = selectedBrands.join('|')

  const [priceDraft, setPriceDraft] = useState({ min: minPrice, max: maxPrice })
  const [priceKey, setPriceKey] = useState(`${minPrice}|${maxPrice}`)
  if (priceKey !== `${minPrice}|${maxPrice}`) {
    setPriceKey(`${minPrice}|${maxPrice}`)
    setPriceDraft({ min: minPrice, max: maxPrice })
  }

  useEffect(() => {
    categoryService.getCategories().then(setCategories).catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    productService
      .getBrands(categoryId || undefined)
      .then(setBrands)
      .catch(() => setBrands([]))
  }, [categoryId])

  const load = useCallback(() => {
    setResult(null)
    setError(null)
    productService
      .getProducts({
        search,
        categoryId,
        brand: brandKey ? brandKey.split('|') : [],
        minPrice,
        maxPrice,
        inStock: inStock || undefined,
        minRating,
        minDiscount,
        sort,
        page,
        size: 12,
      })
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
  }, [search, categoryId, brandKey, minPrice, maxPrice, inStock, minRating, minDiscount, sort, page])

  useEffect(() => {
    load()
  }, [load])

  // Customers only see categories with something to buy: a category an admin just created stays hidden
  // until its first product is approved and live.
  const shopCategories = useMemo(() => categories.filter((c) => c.totalLiveProductCount > 0), [categories])
  const tree = useMemo(() => toCategoryTree(shopCategories), [shopCategories])

  // An old or shared link to a hidden (empty or deactivated) category falls back to all products.
  useEffect(() => {
    if (!categoryId || categories.length === 0) return
    if (!shopCategories.some((c) => String(c.categoryId) === categoryId)) {
      const next = new URLSearchParams(params)
      next.delete('categoryId')
      next.delete('brand')
      next.delete('page')
      setParams(next, { replace: true })
    }
  }, [categoryId, categories, shopCategories, params, setParams])

  /** Updates query params; any filter change resets to page 0 so results never look empty. */
  const update = (changes, keepPage = false) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => {
      next.delete(key)
      if (Array.isArray(value)) value.forEach((v) => next.append(key, v))
      else if (value !== '' && value !== null && value !== undefined && value !== false) next.set(key, String(value))
    })
    if (!keepPage) next.delete('page')
    setParams(next)
  }

  const toggleBrand = (brand) => {
    const nextBrands = selectedBrands.includes(brand)
      ? selectedBrands.filter((b) => b !== brand)
      : [...selectedBrands, brand]
    update({ brand: nextBrands })
  }

  const applyPrice = (event) => {
    event.preventDefault()
    update({ minPrice: priceDraft.min, maxPrice: priceDraft.max })
    setFiltersOpen(false)
  }

  const activeCategory = shopCategories.find((c) => String(c.categoryId) === categoryId)
  const showCategoryHeader = Boolean(activeCategory) && !search
  const subcategories = activeCategory ? tree.find((d) => d.categoryId === activeCategory.categoryId)?.children || [] : []
  const title = search
    ? `Results for “${search}”`
    : activeCategory?.categoryName || (minDiscount ? 'Deals & offers' : 'All products')
  const hasFilters = search || categoryId || selectedBrands.length || minPrice || maxPrice || inStock || minRating || minDiscount
  const categoryOnly = categoryId && !search && !selectedBrands.length && !minPrice && !maxPrice && !inStock && !minRating && !minDiscount
  const priceInvalid = priceDraft.min && priceDraft.max && Number(priceDraft.min) > Number(priceDraft.max)

  const filterPanel = (
    <div className="ss-filter">
      <div className="d-flex justify-content-between align-items-center d-lg-none mb-3">
        <h2 className="h5 m-0">Filters</h2>
        <button type="button" className="ss-close" onClick={() => setFiltersOpen(false)} aria-label="Close filters">
          <i className="bi bi-x-lg" />
        </button>
      </div>

      <div className="ss-filter-group">
        <div className="ss-filter-title">Category</div>
        <button type="button" className={`ss-filter-option ${!categoryId ? 'active' : ''}`} onClick={() => update({ categoryId: '', brand: [] })}>
          All categories
        </button>
        <div className="ss-filter-scroll">
          {tree.map((c) => (
            <div key={c.categoryId}>
              <button
                type="button"
                className={`ss-filter-option ${categoryId === String(c.categoryId) ? 'active' : ''}`}
                onClick={() => update({ categoryId: c.categoryId, brand: [] })}
              >
                {c.categoryName}
                {c.children.length === 0 && <small>{c.totalLiveProductCount}</small>}
              </button>
              {c.children.map((child) => (
                <button
                  type="button"
                  key={child.categoryId}
                  className={`ss-filter-option child ${categoryId === String(child.categoryId) ? 'active' : ''}`}
                  onClick={() => update({ categoryId: child.categoryId, brand: [] })}
                >
                  {child.categoryName}
                  <small>{child.totalLiveProductCount}</small>
                </button>
              ))}
            </div>
          ))}
        </div>
      </div>

      {brands.length > 0 && (
        <fieldset className="ss-filter-group">
          <legend className="ss-filter-title">Brand</legend>
          <div className="ss-brand-list">
            {brands.map((b) => (
              <div className="form-check" key={b}>
                <input
                  className="form-check-input"
                  type="checkbox"
                  id={`brand-${b}`}
                  checked={selectedBrands.includes(b)}
                  onChange={() => toggleBrand(b)}
                />
                <label className="form-check-label" htmlFor={`brand-${b}`}>
                  {b}
                </label>
              </div>
            ))}
          </div>
        </fieldset>
      )}

      <form className="ss-filter-group" onSubmit={applyPrice}>
        <div className="ss-filter-title">Price (₹)</div>
        <div className="d-flex gap-2 align-items-center">
          <input type="number" min="0" className="form-control form-control-sm" placeholder="Min" aria-label="Minimum price"
            value={priceDraft.min} onChange={(e) => setPriceDraft((d) => ({ ...d, min: e.target.value }))} />
          <span className="text-muted">–</span>
          <input type="number" min="0" className="form-control form-control-sm" placeholder="Max" aria-label="Maximum price"
            value={priceDraft.max} onChange={(e) => setPriceDraft((d) => ({ ...d, max: e.target.value }))} />
        </div>
        {priceInvalid && <div className="small text-danger mt-2">Minimum must be lower than maximum.</div>}
        <button type="submit" className="btn btn-outline-ink btn-sm w-100 mt-2" disabled={priceInvalid}>
          Apply price
        </button>
      </form>

      <fieldset className="ss-filter-group">
        <legend className="ss-filter-title">Customer rating</legend>
        {RATINGS.map((r) => (
          <div className="form-check" key={r}>
            <input className="form-check-input" type="radio" name="rating" id={`rating-${r}`} checked={minRating === String(r)}
              onChange={() => update({ minRating: r })} />
            <label className="form-check-label" htmlFor={`rating-${r}`}>
              {r}★ &amp; above
            </label>
          </div>
        ))}
        {minRating && (
          <button type="button" className="btn-link-ink small" onClick={() => update({ minRating: '' })}>
            Clear rating
          </button>
        )}
      </fieldset>

      <fieldset className="ss-filter-group">
        <legend className="ss-filter-title">Discount</legend>
        {DISCOUNTS.map((d) => (
          <div className="form-check" key={d}>
            <input className="form-check-input" type="radio" name="discount" id={`discount-${d}`} checked={minDiscount === String(d)}
              onChange={() => update({ minDiscount: d })} />
            <label className="form-check-label" htmlFor={`discount-${d}`}>
              {d}% or more
            </label>
          </div>
        ))}
        {minDiscount && (
          <button type="button" className="btn-link-ink small" onClick={() => update({ minDiscount: '' })}>
            Clear discount
          </button>
        )}
      </fieldset>

      <div className="ss-filter-group">
        <div className="ss-filter-title">Availability</div>
        <div className="form-check">
          <input className="form-check-input" type="checkbox" id="filter-in-stock" checked={inStock}
            onChange={(e) => update({ inStock: e.target.checked })} />
          <label className="form-check-label" htmlFor="filter-in-stock">
            In stock only
          </label>
        </div>
      </div>

      {hasFilters && (
        <div className="ss-filter-group">
          <button type="button" className="btn btn-ghost btn-sm w-100" onClick={() => setParams({})}>
            Clear all filters
          </button>
        </div>
      )}
    </div>
  )

  const chips = [
    search && { key: 'search', label: `“${search}”`, clear: { search: '' } },
    activeCategory && { key: 'cat', label: activeCategory.categoryName, clear: { categoryId: '', brand: [] } },
    ...selectedBrands.map((b) => ({ key: `brand-${b}`, label: b, clear: { brand: selectedBrands.filter((x) => x !== b) } })),
    (minPrice || maxPrice) && {
      key: 'price',
      label: `${minPrice ? formatPrice(minPrice) : '₹0'} – ${maxPrice ? formatPrice(maxPrice) : 'any'}`,
      clear: { minPrice: '', maxPrice: '' },
    },
    minRating && { key: 'rating', label: `${minRating}★ & above`, clear: { minRating: '' } },
    minDiscount && { key: 'discount', label: `${minDiscount}%+ off`, clear: { minDiscount: '' } },
    inStock && { key: 'stock', label: 'In stock', clear: { inStock: false } },
  ].filter(Boolean)

  return (
    <div className="container ss-page">
      <div className="ss-section-head mb-3">
        <div className="d-flex align-items-center gap-3 min-w-0">
          {showCategoryHeader && activeCategory.imageUrl && (
            <div className="ss-cat-heading-img">
              <SafeImage src={activeCategory.imageUrl} alt="" width={160} eager />
            </div>
          )}
          <div className="min-w-0">
            <h1 className="h3 mb-0">{title}</h1>
            {showCategoryHeader && activeCategory.description && (
              <p className="text-muted mb-0">{activeCategory.description}</p>
            )}
            {result && (
              <p className="text-muted mb-0 small">
                {result.totalElements} {result.totalElements === 1 ? 'product' : 'products'}
                {result.totalPages > 1 && ` · page ${result.page + 1} of ${result.totalPages}`}
              </p>
            )}
          </div>
        </div>
        <div className="d-flex gap-2 align-items-center">
          <button type="button" className="btn btn-ghost btn-sm d-lg-none" onClick={() => setFiltersOpen(true)}>
            <i className="bi bi-sliders me-2" />
            Filters
          </button>
          <label htmlFor="sort" className="small text-muted text-nowrap d-none d-sm-inline">
            Sort by
          </label>
          <select id="sort" className="form-select form-select-sm" style={{ minWidth: 180 }} value={sort} onChange={(e) => update({ sort: e.target.value })}>
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* A department (e.g. Books) shows its subcategories as picture tiles, using each category's image. */}
      {showCategoryHeader && subcategories.length > 0 && (
        <div className="row g-3 row-cols-3 row-cols-sm-4 row-cols-md-6 mb-4">
          {subcategories.map((c) => (
            <div key={c.categoryId} className="col">
              <CategoryCard category={c} productCount={c.totalLiveProductCount} />
            </div>
          ))}
        </div>
      )}

      {chips.length > 0 && (
        <div className="d-flex flex-wrap gap-2 mb-3">
          {chips.map((c) => (
            <span key={c.key} className="ss-chip">
              {c.label}
              <button type="button" onClick={() => update(c.clear)} aria-label={`Remove filter ${c.label}`}>
                <i className="bi bi-x" />
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="row g-3">
        <aside className="col-lg-3">
          <div className={`ss-filter-panel ${filtersOpen ? 'open' : ''}`}>{filterPanel}</div>
          {filtersOpen && <div className="ss-drawer-backdrop d-lg-none" onClick={() => setFiltersOpen(false)} />}
        </aside>
        <div className="col-lg-9">
          {error ? (
            <ErrorState message={error} onRetry={load} />
          ) : !result ? (
            <ProductGridSkeleton count={6} cols="col-6 col-md-4" />
          ) : result.content.length === 0 && categoryOnly ? (
            <EmptyState
              icon="bi-box-seam"
              title={`No products in ${activeCategory?.categoryName || 'this category'} yet`}
              action={
                <button type="button" className="btn btn-ink" onClick={() => setParams({})}>
                  Browse all products
                </button>
              }
            >
              New products are added here as soon as sellers list them. Check back soon.
            </EmptyState>
          ) : result.content.length === 0 ? (
            <EmptyState
              icon="bi-search"
              title="No products match"
              action={
                <button type="button" className="btn btn-ink" onClick={() => setParams({})}>
                  Clear filters
                </button>
              }
            >
              Try a different search term, widen the price range or remove a filter.
            </EmptyState>
          ) : (
            <>
              <div className="row g-3">
                {result.content.map((p) => (
                  <div key={p.productId} className="col-6 col-md-4">
                    <ProductCard product={p} />
                  </div>
                ))}
              </div>
              <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => update({ page: p }, true)} />
            </>
          )}
        </div>
      </div>
    </div>
  )
}
