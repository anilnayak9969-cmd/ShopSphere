/** page is 0-based (matches Spring Data). Shows at most 5 page numbers around the current one. */
export default function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null

  const start = Math.max(0, Math.min(page - 2, totalPages - 5))
  const end = Math.min(totalPages, start + 5)
  const pages = Array.from({ length: end - start }, (_, i) => start + i)

  return (
    <nav className="ss-pagination" aria-label="Pagination">
      <button type="button" onClick={() => onChange(page - 1)} disabled={page === 0} aria-label="Previous page">
        <i className="bi bi-chevron-left" />
      </button>
      {start > 0 && <span className="px-1 text-muted">…</span>}
      {pages.map((p) => (
        <button
          type="button"
          key={p}
          className={p === page ? 'active' : ''}
          onClick={() => onChange(p)}
          aria-current={p === page ? 'page' : undefined}
        >
          {p + 1}
        </button>
      ))}
      {end < totalPages && <span className="px-1 text-muted">…</span>}
      <button
        type="button"
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label="Next page"
      >
        <i className="bi bi-chevron-right" />
      </button>
    </nav>
  )
}
