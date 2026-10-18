import { useState } from 'react'
import cartService from '../services/cartService'
import { getErrorMessage } from '../utils/errors'
import { formatPrice } from '../utils/format'

/**
 * Coupon input. The server validates the code against the current cart and returns the real
 * discount (CouponQuote); this component only displays it.
 */
export default function CouponBox({ quote, onApplied, onRemoved, disabled = false, id = 'coupon' }) {
  const [code, setCode] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const apply = async (event) => {
    event?.preventDefault()
    if (!code.trim() || busy) return
    setBusy(true)
    setError(null)
    try {
      onApplied(await cartService.applyCoupon(code.trim()))
      setCode('')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (quote) {
    return (
      <div className="ss-coupon-applied">
        <i className="bi bi-ticket-perforated" aria-hidden="true" />
        <div className="flex-grow-1 min-w-0">
          <strong>{quote.code}</strong> applied — you save {formatPrice(quote.discount)}
          {quote.description && <div className="small text-muted">{quote.description}</div>}
        </div>
        <button type="button" className="btn-link-ink small" onClick={onRemoved} disabled={disabled}>
          Remove
        </button>
      </div>
    )
  }

  return (
    <div>
      <label htmlFor={id} className="form-label">
        Have a coupon?
      </label>
      <div className="d-flex gap-2">
        <input
          id={id}
          className={`form-control ${error ? 'is-invalid' : ''}`}
          placeholder="Enter code"
          value={code}
          maxLength={30}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          onKeyDown={(e) => e.key === 'Enter' && apply(e)}
          disabled={disabled}
          aria-describedby={error ? `${id}-error` : undefined}
        />
        <button type="button" className="btn btn-outline-ink" onClick={apply} disabled={disabled || busy || !code.trim()}>
          {busy ? <span className="spinner-border spinner-border-sm" aria-label="Checking" /> : 'Apply'}
        </button>
      </div>
      {error && (
        <div id={`${id}-error`} className="invalid-feedback d-block">
          {error}
        </div>
      )}
    </div>
  )
}
