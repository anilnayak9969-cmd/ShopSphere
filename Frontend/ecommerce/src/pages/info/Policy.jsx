import { useEffect, useState } from 'react'
import { NavLink, useParams } from 'react-router-dom'
import publicService from '../../services/publicService'
import NotFound from '../NotFound'
import { formatPrice } from '../../utils/format'

/**
 * Store policies. Numbers (free-delivery threshold, shipping fee, return window, COD limit) are read
 * from the live platform settings, so the text always matches what checkout actually does.
 */
function sections(s) {
  const free = s ? formatPrice(s.freeShippingThreshold) : 'the free-delivery threshold'
  const fee = s ? formatPrice(s.shippingFee) : 'a small fee'
  const days = s ? `${s.returnWindowDays} days` : 'the return window'
  return {
    shipping: {
      title: 'Shipping information',
      body: [
        ['Delivery charges', `Delivery is free on orders of ${free} or more (after any coupon). Smaller orders pay a flat ${fee}, shown before you pay.`],
        ['Who ships your order', 'Each seller packs and ships their own items. An order with items from several stores may arrive in separate parcels.'],
        ['Tracking', 'When a ShopSphere delivery partner picks up your item, a tracking number appears on your order page. Every step updates live, and you get a notification each time.'],
        ['Delivery estimates', 'ShopSphere does not invent delivery dates. Progress is shown as each seller confirms, packs and ships your items.'],
      ],
    },
    returns: {
      title: 'Returns & refunds',
      body: [
        ['Cancelling an order', 'You can cancel online while all items are Placed or Confirmed. Items already packed or shipped cannot be cancelled online.'],
        ['Returning an item', `Request a return from your order page within ${days} of delivery. The seller (or our team) reviews the request.`],
        ['Refunds', 'Approved returns and cancellations are refunded for the amount you paid for those items (after their share of any coupon). Online payments go back to the original method through Razorpay.'],
        ['Cash on Delivery', 'If a Cash on Delivery order is cancelled before delivery, nothing is charged. Refunds for collected COD payments are processed manually by our team.'],
      ],
    },
    privacy: {
      title: 'Privacy policy',
      body: [
        ['What we store', 'Your name, email, phone, delivery addresses, orders and reviews. Passwords are stored only as secure hashes.'],
        ['Payments', 'Card numbers, CVVs and UPI PINs are entered on Razorpay and never reach ShopSphere. We keep only Razorpay payment references.'],
        ['Who can see your data', 'Sellers see only the items they sold to you and the delivery address needed to ship them. Your reviews show only your first name.'],
        ['Email', 'We send one-time codes for verification and password reset, and order confirmations.'],
      ],
    },
    terms: {
      title: 'Terms & conditions',
      body: [
        ['Accounts', 'Keep your password private. Accounts may be suspended for fraud or abuse.'],
        ['Prices', 'Prices include all taxes. The price charged is the one calculated by our server when you check out.'],
        ['Sellers', 'Sellers must be approved, list only genuine products and keep stock accurate. Listings are reviewed before going live.'],
        ['Reviews', 'Only customers who received a product can review it, once per product. Abusive reviews are removed.'],
        ['Demo store', 'This is an internship project. Demo products are sample data and orders are not fulfilled.'],
      ],
    },
  }
}

const LINKS = [
  ['shipping', 'Shipping'],
  ['returns', 'Returns & refunds'],
  ['privacy', 'Privacy'],
  ['terms', 'Terms'],
]

export default function Policy() {
  const { slug } = useParams()
  const [settings, setSettings] = useState(null)

  useEffect(() => {
    publicService.getSettings().then(setSettings).catch(() => setSettings(null))
  }, [])

  const page = sections(settings)[slug]
  if (!page) return <NotFound />

  return (
    <div className="container ss-page" style={{ maxWidth: 1040 }}>
      <div className="row g-4">
        <aside className="col-md-3">
          <nav className="ss-card p-2 ss-side-nav" aria-label="Policies">
            {LINKS.map(([key, label]) => (
              <NavLink key={key} to={`/policies/${key}`}>
                {label}
              </NavLink>
            ))}
          </nav>
        </aside>
        <article className="col-md-9">
          <span className="ss-eyebrow">Policies</span>
          <h1 className="mt-1">{page.title}</h1>
          <div className="ss-card ss-card-pad ss-prose mt-3">
            {page.body.map(([heading, text]) => (
              <section key={heading}>
                <h2>{heading}</h2>
                <p>{text}</p>
              </section>
            ))}
          </div>
        </article>
      </div>
    </div>
  )
}
