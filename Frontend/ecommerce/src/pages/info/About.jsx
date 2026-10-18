import { Link } from 'react-router-dom'

const VALUES = [
  { icon: 'bi-patch-check', title: 'Trust first', text: 'Sellers are approved by our team, and every new or changed listing is reviewed before customers see it.' },
  { icon: 'bi-shield-lock', title: 'Safe payments', text: 'Online payments are verified on our servers with Razorpay signatures. Card details never touch ShopSphere.' },
  { icon: 'bi-people', title: 'Fair to sellers', text: 'Each seller manages their own products, stock and fulfilment, with clear sales and commission reports.' },
]

export default function About() {
  return (
    <div className="container ss-page" style={{ maxWidth: 960 }}>
      <span className="ss-eyebrow">About us</span>
      <h1 className="mt-1">One marketplace, many independent sellers</h1>
      <p className="lead text-muted">
        ShopSphere brings product categories and independent stores together in a single, easy shopping destination.
      </p>

      <div className="row g-3 my-4">
        {VALUES.map((v) => (
          <div key={v.title} className="col-md-4">
            <div className="ss-card ss-card-pad h-100">
              <i className={`bi ${v.icon} fs-3 text-primary`} aria-hidden="true" />
              <h2 className="h5 mt-2">{v.title}</h2>
              <p className="text-muted mb-0">{v.text}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="ss-prose">
        <h2>How it works</h2>
        <p>
          Customers browse and buy from many stores in one cart. Each seller confirms, packs and ships their own items,
          and you can follow every item&apos;s progress from your order page. Payments are taken securely online or on
          delivery, and refunds for cancelled or returned items go back to the original payment method.
        </p>
        <h2>About this project</h2>
        <p>
          ShopSphere is an internship project built with React, Spring Boot and MySQL. Products marked &ldquo;Demo
          product&rdquo; are sample data used for testing.
        </p>
      </div>

      <div className="d-flex gap-2 flex-wrap mt-4">
        <Link to="/products" className="btn btn-ink">
          Start shopping
        </Link>
        <Link to="/contact" className="btn btn-ghost">
          Contact us
        </Link>
      </div>
    </div>
  )
}
