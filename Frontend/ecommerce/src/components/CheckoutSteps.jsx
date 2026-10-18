const STEPS = ['Address', 'Review', 'Payment', 'Confirmation']

/** 1 Address → 2 Review → 3 Payment → 4 Confirmation. `current` is 0-based. */
export default function CheckoutSteps({ current }) {
  return (
    <ol className="ss-steps" aria-label="Checkout progress">
      {STEPS.map((label, i) => (
        <li key={label} className={i < current ? 'done' : i === current ? 'current' : ''} aria-current={i === current ? 'step' : undefined}>
          <span className="ss-steps-dot">{i < current ? <i className="bi bi-check-lg" aria-hidden="true" /> : i + 1}</span>
          <span className="ss-steps-label">{label}</span>
        </li>
      ))}
    </ol>
  )
}
