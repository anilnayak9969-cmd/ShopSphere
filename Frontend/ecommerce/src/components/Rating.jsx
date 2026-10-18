/** Read-only star display, e.g. ★★★★☆ 4.3 (12). Renders nothing when there are no reviews yet. */
export function Stars({ value = 0, count, size = 'sm', showValue = true }) {
  if (count === 0) return null
  const rounded = Math.round(Number(value) * 2) / 2
  return (
    <span className={`ss-stars ss-stars-${size}`} aria-label={`Rated ${Number(value).toFixed(1)} out of 5${count ? ` from ${count} reviews` : ''}`}>
      <span className="ss-stars-icons" aria-hidden="true">
        {[1, 2, 3, 4, 5].map((i) => (
          <i key={i} className={`bi ${rounded >= i ? 'bi-star-fill' : rounded >= i - 0.5 ? 'bi-star-half' : 'bi-star'}`} />
        ))}
      </span>
      {showValue && <span className="ss-stars-value">{Number(value).toFixed(1)}</span>}
      {count != null && <span className="ss-stars-count">({count})</span>}
    </span>
  )
}

/** Clickable 1-5 star picker for the review form. */
export function StarInput({ value, onChange, disabled = false }) {
  const labels = ['Poor', 'Fair', 'Good', 'Very good', 'Excellent']
  return (
    <div className="ss-star-input" role="radiogroup" aria-label="Your rating">
      {[1, 2, 3, 4, 5].map((i) => (
        <button
          type="button"
          key={i}
          role="radio"
          aria-checked={value === i}
          aria-label={`${i} star${i > 1 ? 's' : ''} — ${labels[i - 1]}`}
          className={value >= i ? 'on' : ''}
          onClick={() => onChange(i)}
          disabled={disabled}
        >
          <i className={`bi ${value >= i ? 'bi-star-fill' : 'bi-star'}`} />
        </button>
      ))}
      {value > 0 && <span className="small text-muted ms-2">{labels[value - 1]}</span>}
    </div>
  )
}
