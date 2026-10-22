import { useEffect, useRef } from 'react'

/**
 * Accessible modal dialog: closes on Escape or backdrop click, moves focus inside when opened,
 * and returns focus to the previously focused element when closed.
 */
export default function Modal({ open, title, subtitle, onClose, children, footer, size = '' }) {
  const dialogRef = useRef(null)
  // Callers often pass a new onClose on every render; keep the latest in a ref so the effect below runs only
  // when the dialog opens/closes — otherwise each keystroke would re-run it and yank focus back to the first field.
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    if (!open) return undefined
    const previouslyFocused = document.activeElement
    const focusable = dialogRef.current?.querySelector('input, select, textarea, button:not(.ss-close)')
    ;(focusable || dialogRef.current)?.focus()

    const onKey = (event) => {
      if (event.key === 'Escape') onCloseRef.current?.()
    }
    document.addEventListener('keydown', onKey)
    const { overflow } = document.body.style
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      previouslyFocused?.focus?.()
    }
  }, [open])

  if (!open) return null

  return (
    <div className="ss-modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose?.()}>
      <div
        ref={dialogRef}
        className={`ss-modal ${size === 'lg' ? 'ss-modal-lg' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="ss-modal-title"
        tabIndex={-1}
      >
        <div className="ss-modal-header">
          <div>
            <h2 id="ss-modal-title">{title}</h2>
            {subtitle && <p className="text-muted small mb-0 mt-1">{subtitle}</p>}
          </div>
          {onClose && (
            <button type="button" className="ss-close" onClick={onClose} aria-label="Close">
              <i className="bi bi-x-lg" />
            </button>
          )}
        </div>
        <div className="ss-modal-body">{children}</div>
        {footer && <div className="ss-modal-footer">{footer}</div>}
      </div>
    </div>
  )
}

/** Yes/no confirmation built on Modal, used before destructive actions. */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel = 'Confirm',
  danger = false,
  busy = false,
  onConfirm,
  onCancel,
}) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={busy ? undefined : onCancel}
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className={`btn ${danger ? 'btn-danger-soft' : 'btn-ink'}`}
            onClick={onConfirm}
            disabled={busy}
          >
            {busy && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
            {confirmLabel}
          </button>
        </>
      }
    >
      <div className="text-muted">{children}</div>
    </Modal>
  )
}
