import { useState } from 'react'
import { Link } from 'react-router-dom'
import { resolveImageUrl } from '../services/api'
import { formatPrice, labelize } from '../utils/format'

/** Asks image CDNs that support it (Unsplash) for a smaller file, e.g. thumbnails at 300 px instead of 900 px. */
function sized(url, width) {
  if (!width || !url || !url.includes('images.unsplash.com')) return url
  return /[?&]w=\d+/.test(url) ? url.replace(/([?&]w=)\d+/, `$1${width}`) : `${url}${url.includes('?') ? '&' : '?'}w=${width}`
}

/**
 * Product image with a graceful placeholder when the URL is missing or broken. Handles uploaded images too.
 * `width` requests a right-sized file from the image CDN where possible.
 */
export function SafeImage({ src, alt, className = '', eager = false, width }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return (
      <div className={`ss-img-fallback ${className}`} role="img" aria-label={alt}>
        <i className="bi bi-image" aria-hidden="true" />
      </div>
    )
  }
  return (
    <img
      src={sized(resolveImageUrl(src), width)}
      alt={alt}
      className={className}
      loading={eager ? 'eager' : 'lazy'}
      decoding="async"
      onError={() => setFailed(true)}
    />
  )
}

/** Orange "25% OFF" label, shown only for genuine discounts. */
export function DiscountBadge({ percent, className = 'ss-product-tag' }) {
  if (!percent || percent <= 0) return null
  return <span className={className}>{percent}% OFF</span>
}

export function PriceTag({ price, sellingPrice, discountPercent, large = false }) {
  return (
    <div className="ss-price-row">
      <span className={`ss-price ${large ? 'ss-price-lg' : ''}`}>{formatPrice(sellingPrice)}</span>
      {discountPercent > 0 && (
        <>
          <span className="ss-mrp">{formatPrice(price)}</span>
          <span className="ss-off">{discountPercent}% off</span>
        </>
      )}
    </div>
  )
}

export function StockNote({ stock }) {
  if (stock <= 0) return <span className="ss-stock ss-stock-out">Out of stock</span>
  if (stock <= 5) return <span className="ss-stock ss-stock-low">Only {stock} left</span>
  return <span className="ss-stock ss-stock-ok">In stock</span>
}

export function Spinner({ small = false, label = 'Loading' }) {
  return (
    <span className={`spinner-border ${small ? 'spinner-border-sm' : ''}`} role="status">
      <span className="visually-hidden">{label}</span>
    </span>
  )
}

export function PageLoader({ label = 'Loading…' }) {
  return (
    <div className="d-flex flex-column align-items-center justify-content-center gap-3 py-5 my-5 text-muted">
      <Spinner />
      <span className="small">{label}</span>
    </div>
  )
}

export function ProductGridSkeleton({ count = 8, cols = 'col-6 col-md-4 col-xl-3' }) {
  return (
    <div className="row g-3 g-md-4">
      {Array.from({ length: count }, (_, i) => (
        <div className={cols} key={i}>
          <div className="ss-product-card" aria-hidden="true">
            <div className="ss-skeleton" style={{ aspectRatio: '1 / 1', borderRadius: 0 }} />
            <div className="ss-product-body">
              <div className="ss-skeleton" style={{ height: 10, width: '40%' }} />
              <div className="ss-skeleton" style={{ height: 18, width: '85%' }} />
              <div className="ss-skeleton" style={{ height: 14, width: '50%' }} />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

export function EmptyState({ icon = 'bi-bag', title, children, action }) {
  return (
    <div className="ss-empty">
      <div className="ss-empty-icon">
        <i className={`bi ${icon}`} aria-hidden="true" />
      </div>
      <h3>{title}</h3>
      {children && <p className="text-muted mx-auto" style={{ maxWidth: 440 }}>{children}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry }) {
  return (
    <EmptyState
      icon="bi-wifi-off"
      title="Something went wrong"
      action={
        onRetry && (
          <button type="button" className="btn btn-ink" onClick={onRetry}>
            <i className="bi bi-arrow-clockwise me-2" />
            Try again
          </button>
        )
      }
    >
      {message}
    </EmptyState>
  )
}

export function Alert({ variant = 'info', icon, children, className = '' }) {
  const defaultIcon = {
    info: 'bi-info-circle',
    warning: 'bi-exclamation-triangle',
    danger: 'bi-x-octagon',
    success: 'bi-check-circle',
  }[variant]
  return (
    <div className={`ss-alert ss-alert-${variant} ${className}`} role={variant === 'danger' ? 'alert' : undefined}>
      <i className={`bi ${icon || defaultIcon}`} aria-hidden="true" />
      <div>{children}</div>
    </div>
  )
}

const STATUS_VARIANTS = {
  // orders
  PENDING_PAYMENT: 'muted',
  PLACED: 'info',
  CONFIRMED: 'info',
  PROCESSING: 'gold',
  PACKED: 'gold',
  SHIPPED: 'gold',
  OUT_FOR_DELIVERY: 'gold',
  DELIVERED: 'success',
  CANCELLATION_REQUESTED: 'warning',
  CANCELLED: 'danger',
  RETURN_REQUESTED: 'warning',
  RETURNED: 'muted',
  // payments
  CREATED: 'muted',
  PENDING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  REFUNDED: 'muted',
  // returns
  REQUESTED: 'warning',
  // sellers
  APPROVED: 'success',
  REJECTED: 'danger',
  SUSPENDED: 'danger',
}

export function StatusBadge({ status, label }) {
  if (!status) return null
  return <span className={`ss-pill ss-pill-${STATUS_VARIANTS[status] || 'muted'}`}>{label || labelize(status)}</span>
}

export function ActiveBadge({ active, on = 'Active', off = 'Inactive' }) {
  return <span className={`ss-pill ${active ? 'ss-pill-success' : 'ss-pill-muted'}`}>{active ? on : off}</span>
}

export function QuantityStepper({ value, min = 1, max = 10, onChange, disabled = false, large = false, label }) {
  return (
    <div className={`ss-qty ${large ? 'ss-qty-lg' : ''}`} role="group" aria-label={label || 'Quantity'}>
      <button type="button" onClick={() => onChange(value - 1)} disabled={disabled || value <= min} aria-label="Decrease">
        <i className="bi bi-dash" />
      </button>
      <span aria-live="polite">{value}</span>
      <button type="button" onClick={() => onChange(value + 1)} disabled={disabled || value >= max} aria-label="Increase">
        <i className="bi bi-plus" />
      </button>
    </div>
  )
}

export function PageHeader({ eyebrow, title, subtitle, actions }) {
  return (
    <div className="ss-page-head">
      <div>
        {eyebrow && <span className="ss-eyebrow">{eyebrow}</span>}
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div className="d-flex gap-2 flex-wrap">{actions}</div>}
    </div>
  )
}

/** Dashboard figure. Pass `to` to make the whole card a link to the related page. */
export function StatCard({ label, value, icon, foot, dark = false, to }) {
  const Tag = to ? Link : 'div'
  return (
    <Tag to={to} className={`ss-stat ${dark ? 'ss-stat-dark' : ''}`}>
      <div className="ss-stat-top">
        <span className="ss-stat-label">{label}</span>
        <span className="ss-stat-icon">
          <i className={`bi ${icon}`} aria-hidden="true" />
        </span>
      </div>
      <div className="ss-stat-value">{value}</div>
      {foot && <div className="ss-stat-foot">{foot}</div>}
    </Tag>
  )
}

/** Pill-style tab switcher. options: [{ value, label }] */
export function Segment({ options, value, onChange, label }) {
  return (
    <div className="ss-segment" role="group" aria-label={label}>
      {options.map((o) => (
        <button type="button" key={o.value} className={value === o.value ? 'active' : ''} aria-pressed={value === o.value} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  )
}
