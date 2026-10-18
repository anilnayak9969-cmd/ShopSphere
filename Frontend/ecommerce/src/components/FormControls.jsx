import { useRef, useState } from 'react'
import { PASSWORD_RULES } from '../utils/validators'

/** Password field with a show/hide toggle and optional live rule checklist. */
export function PasswordInput({ id, label, value, onChange, error, showRules = false, autoComplete = 'current-password' }) {
  const [visible, setVisible] = useState(false)
  return (
    <div>
      <label htmlFor={id} className="form-label">
        {label}
      </label>
      <div className="ss-input-icon">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          className={`form-control ${error ? 'is-invalid' : ''}`}
          value={value}
          onChange={onChange}
          autoComplete={autoComplete}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
        />
        <button
          type="button"
          className="ss-input-toggle"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
        >
          <i className={`bi ${visible ? 'bi-eye-slash' : 'bi-eye'}`} />
        </button>
      </div>
      {error && (
        <div id={`${id}-error`} className="invalid-feedback d-block">
          {error}
        </div>
      )}
      {showRules && (
        <div className="ss-strength">
          {PASSWORD_RULES.map((rule) => {
            const ok = rule.test(value)
            return (
              <span key={rule.label} className={ok ? 'ok' : ''}>
                <i className={`bi ${ok ? 'bi-check-circle-fill' : 'bi-circle'} me-1`} aria-hidden="true" />
                {rule.label}
              </span>
            )
          })}
        </div>
      )}
    </div>
  )
}

/** Six single-digit boxes. Supports typing, backspace navigation and pasting the whole code. */
export function OtpInput({ value, onChange, invalid = false, disabled = false }) {
  const refs = useRef([])
  const digits = Array.from({ length: 6 }, (_, i) => value[i] || '')

  const setDigit = (index, digit) => {
    const next = digits.slice()
    next[index] = digit
    onChange(next.join(''))
  }

  const handleChange = (index, raw) => {
    const clean = raw.replace(/\D/g, '')
    if (clean.length > 1) {
      // Pasted or autofilled several digits.
      const merged = (digits.slice(0, index).join('') + clean).slice(0, 6)
      onChange(merged)
      refs.current[Math.min(merged.length, 5)]?.focus()
      return
    }
    setDigit(index, clean)
    if (clean && index < 5) refs.current[index + 1]?.focus()
  }

  const handleKeyDown = (index, event) => {
    if (event.key === 'Backspace' && !digits[index] && index > 0) {
      refs.current[index - 1]?.focus()
      setDigit(index - 1, '')
    } else if (event.key === 'ArrowLeft' && index > 0) {
      refs.current[index - 1]?.focus()
    } else if (event.key === 'ArrowRight' && index < 5) {
      refs.current[index + 1]?.focus()
    }
  }

  return (
    <div className={`ss-otp ${invalid ? 'is-invalid' : ''}`} role="group" aria-label="6-digit verification code">
      {digits.map((digit, index) => (
        <input
          key={index}
          ref={(el) => (refs.current[index] = el)}
          type="text"
          inputMode="numeric"
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          maxLength={6}
          value={digit}
          disabled={disabled}
          aria-label={`Digit ${index + 1}`}
          onChange={(e) => handleChange(index, e.target.value)}
          onKeyDown={(e) => handleKeyDown(index, e)}
          onFocus={(e) => e.target.select()}
        />
      ))}
    </div>
  )
}

/** Labelled input / textarea / select with Bootstrap invalid styling. For a select, pass <option>s as children. */
export function Field({ id, label, error, hint, as = 'input', children, ...props }) {
  const Control = as
  return (
    <div>
      <label htmlFor={id} className="form-label">
        {label}
      </label>
      <Control
        id={id}
        className={`${as === 'select' ? 'form-select' : 'form-control'} ${error ? 'is-invalid' : ''}`}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...props}
      >
        {as === 'select' ? children : undefined}
      </Control>
      {error ? (
        <div id={`${id}-error`} className="invalid-feedback d-block">
          {error}
        </div>
      ) : (
        hint && (
          <div id={`${id}-hint`} className="form-text">
            {hint}
          </div>
        )
      )}
    </div>
  )
}
