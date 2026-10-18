import { useState } from 'react'
import { Link } from 'react-router-dom'

// Every answer describes how this application actually behaves.
const SECTIONS = [
  {
    id: 'shipping',
    icon: 'bi-truck',
    title: 'Shipping',
    items: [
      ['How much does delivery cost?', 'Delivery is free on orders of ₹499 or more after any coupon discount. Below that, a flat ₹49 is added and shown before you pay.'],
      ['Where can I see my order status?', 'Open My account → Orders. Each order shows a timeline from Placed to Delivered, updated as the order moves.'],
    ],
  },
  {
    id: 'cancellation',
    icon: 'bi-arrow-counterclockwise',
    title: 'Cancellations & refunds',
    items: [
      ['Can I cancel my order?', 'Yes — while an order is Placed or Confirmed you can cancel it yourself from the order page. After it is being processed, contact support.'],
      ['How do refunds work?', 'When a paid order is cancelled, the items go back into stock and the full amount is refunded through Razorpay to your original payment method.'],
    ],
  },
  {
    id: 'payments',
    icon: 'bi-shield-lock',
    title: 'Payments & security',
    items: [
      ['Which payment methods are accepted?', 'UPI, debit and credit cards, net banking and wallets, all processed by Razorpay in its secure window.'],
      ['Do you store my card details?', 'No. ShopSphere never sees or stores card numbers, CVVs or UPI PINs. We only keep Razorpay’s payment reference.'],
      ['How do coupons work?', 'Enter a code at checkout. The discount is calculated by our server, and each coupon can be used once per customer.'],
    ],
  },
  {
    id: 'account',
    icon: 'bi-person-check',
    title: 'Account & sellers',
    items: [
      ['Why do I need to verify my email?', 'A one-time code confirms the address belongs to you. It also protects password resets.'],
      ['How do I become a seller?', 'Register and choose “Sell”. After verifying your email, our team reviews your store; once approved you can list products.'],
      ['Who can write reviews?', 'Only customers who bought a product. Every review is marked as a verified purchase.'],
    ],
  },
]

export default function Help() {
  const [open, setOpen] = useState('shipping-0')

  return (
    <div className="container ss-page" style={{ maxWidth: 980 }}>
      <span className="ss-eyebrow">Help centre</span>
      <h1 className="mt-1" style={{ fontSize: 'clamp(2.4rem, 4vw, 3.2rem)' }}>
        How can we help?
      </h1>
      <p className="text-muted mb-4">Shipping, cancellations, payments and accounts — everything in one place.</p>

      <div className="d-flex flex-wrap gap-2 mb-5">
        {SECTIONS.map((s) => (
          <a key={s.id} href={`#${s.id}`} className="ss-chip text-decoration-none text-body" style={{ paddingRight: '0.9rem' }}>
            <i className={`bi ${s.icon}`} aria-hidden="true" /> {s.title}
          </a>
        ))}
      </div>

      {SECTIONS.map((section) => (
        <section key={section.id} id={section.id} className="mb-5">
          <h2 className="h3 mb-3">
            <i className={`bi ${section.icon} me-2`} style={{ color: 'var(--ss-gold-deep)' }} aria-hidden="true" />
            {section.title}
          </h2>
          <div className="ss-card">
            {section.items.map(([q, a], i) => {
              const key = `${section.id}-${i}`
              const expanded = open === key
              return (
                <div key={key} className="ss-faq">
                  <button type="button" aria-expanded={expanded} aria-controls={`${key}-answer`} onClick={() => setOpen(expanded ? null : key)}>
                    <span>{q}</span>
                    <i className={`bi ${expanded ? 'bi-dash' : 'bi-plus'}`} aria-hidden="true" />
                  </button>
                  {expanded && (
                    <p id={`${key}-answer`} className="text-muted mb-0">
                      {a}
                    </p>
                  )}
                </div>
              )
            })}
          </div>
        </section>
      ))}

      <div className="ss-band">
        <div className="row align-items-center g-3">
          <div className="col-md-8">
            <h2 className="h1 mb-2">Still need help?</h2>
            <p className="mb-0">Sign in and open your order — every update and refund status is recorded there.</p>
          </div>
          <div className="col-md-4 text-md-end">
            <Link to="/account/orders" className="btn btn-gold btn-lg">
              Go to my orders
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
