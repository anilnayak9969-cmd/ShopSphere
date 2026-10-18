import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import productService from '../services/productService'
import useDebounce from '../hooks/useDebounce'

/** Search box with live suggestions. Up/Down to move, Enter to open, Escape to close. */
export default function SearchBar({ className = '', id = 'site-search' }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const urlSearch = location.pathname === '/products' ? params.get('search') ?? '' : ''

  const [query, setQuery] = useState(urlSearch)
  const [syncedSearch, setSyncedSearch] = useState(urlSearch)
  const [suggestions, setSuggestions] = useState([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const wrapperRef = useRef(null)
  const debounced = useDebounce(query.trim(), 250)

  // Keep the box in sync when the URL changes (e.g. clicking a category clears the search).
  if (urlSearch !== syncedSearch) {
    setSyncedSearch(urlSearch)
    setQuery(urlSearch)
  }

  useEffect(() => {
    if (debounced.length < 2) return undefined
    let cancelled = false
    productService
      .getSuggestions(debounced)
      .then((data) => {
        if (!cancelled) {
          setSuggestions(data)
          setActive(-1)
        }
      })
      .catch(() => !cancelled && setSuggestions([]))
    return () => {
      cancelled = true
    }
  }, [debounced])

  useEffect(() => {
    const onClick = (event) => {
      if (!wrapperRef.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [])

  const visibleSuggestions = debounced.length >= 2 ? suggestions : []

  const submit = (event) => {
    event.preventDefault()
    if (active >= 0 && visibleSuggestions[active]) {
      openProduct(visibleSuggestions[active].productId)
      return
    }
    setOpen(false)
    const q = query.trim()
    navigate(q ? `/products?search=${encodeURIComponent(q)}` : '/products')
  }

  const openProduct = (productId) => {
    setOpen(false)
    navigate(`/products/${productId}`)
  }

  const onKeyDown = (event) => {
    if (!open || visibleSuggestions.length === 0) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((i) => (i + 1) % visibleSuggestions.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((i) => (i <= 0 ? visibleSuggestions.length - 1 : i - 1))
    } else if (event.key === 'Escape') {
      setOpen(false)
    }
  }

  const listId = `${id}-suggestions`

  return (
    <form role="search" className={`ss-search ${className}`} onSubmit={submit} ref={wrapperRef}>
      <label htmlFor={id} className="visually-hidden">
        Search products
      </label>
      <input
        id={id}
        type="search"
        placeholder="Search watches, sneakers, headphones…"
        autoComplete="off"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKeyDown}
        role="combobox"
        aria-expanded={open && visibleSuggestions.length > 0}
        aria-controls={listId}
        aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
      />
      <button type="submit" className="ss-search-btn" aria-label="Search">
        <i className="bi bi-search" />
      </button>
      {open && visibleSuggestions.length > 0 && (
        <ul className="ss-suggestions" id={listId} role="listbox">
          {visibleSuggestions.map((s, index) => (
            <li key={s.productId} role="option" id={`${listId}-${index}`} aria-selected={index === active}>
              <button
                type="button"
                className={`ss-suggestion ${index === active ? 'active' : ''}`}
                onMouseEnter={() => setActive(index)}
                onClick={() => openProduct(s.productId)}
              >
                <span>
                  <i className="bi bi-search me-2 text-muted" aria-hidden="true" />
                  {s.productName}
                </span>
                <small>{s.categoryName}</small>
              </button>
            </li>
          ))}
        </ul>
      )}
    </form>
  )
}
