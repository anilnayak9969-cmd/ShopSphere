/**
 * What each role is for, what it needs at sign-up and how the sign-in / sign-up pages present it.
 * ADMIN accounts are never self-registered: they are created by the backend (see README).
 */
export const ROLE_INFO = {
  CUSTOMER: {
    label: 'Customer',
    icon: 'bi-bag-heart',
    title: 'Shop',
    tagline: 'Buy from verified sellers',
    needs: ['Name', 'Email', 'Mobile'],
    loginHint: 'Track orders, manage your wishlist and check out faster.',
    asideTitle: 'Join a marketplace worth browsing.',
    asideText: 'Create your account, verify your email with a one-time code and start shopping in under a minute.',
    image: 'https://images.unsplash.com/photo-1483985988355-763728e1935b?w=1400&q=80',
    points: [
      { icon: 'bi-patch-check', text: 'Every seller and product is reviewed before it goes live' },
      { icon: 'bi-shield-lock', text: 'Pay with UPI, cards or net banking via Razorpay, or cash on delivery' },
      { icon: 'bi-arrow-counterclockwise', text: 'Easy returns from your order page' },
    ],
    submit: 'Create account',
  },
  SELLER: {
    label: 'Seller',
    icon: 'bi-shop',
    title: 'Sell',
    tagline: 'Open your own store',
    needs: ['Store name', 'Business type', 'Pickup address', 'GSTIN (optional)'],
    loginHint: 'Manage your store, products, inventory and orders.',
    asideTitle: 'Bring your store to a discerning audience.',
    asideText: 'Apply in minutes. Our team reviews every store; once approved you can list products straight away.',
    image: 'https://images.unsplash.com/photo-1556740749-887f6717d7e4?w=1400&q=80',
    points: [
      { icon: 'bi-cash-coin', text: 'No registration fee; commission only on delivered orders' },
      { icon: 'bi-graph-up-arrow', text: 'Sales, inventory and earnings reports in your studio' },
      { icon: 'bi-truck', text: 'Couriers collect from your pickup address' },
    ],
    submit: 'Submit seller application',
  },
  DELIVERY_PARTNER: {
    label: 'Delivery partner',
    icon: 'bi-bicycle',
    title: 'Deliver',
    tagline: 'Earn by delivering orders',
    needs: ['Vehicle', 'Service city & PIN'],
    loginHint: 'See your application status and delivery profile.',
    asideTitle: 'Deliver on your own schedule.',
    asideText: 'Tell us where and how you ride. We review every application and invite approved partners to onboarding.',
    image: 'https://images.unsplash.com/photo-1526367790999-0150786686a2?w=1400&q=80',
    points: [
      { icon: 'bi-calendar-check', text: 'Choose the area you want to deliver in' },
      { icon: 'bi-person-vcard', text: 'Bring your driving licence and PAN to onboarding — no uploads needed' },
      { icon: 'bi-bell', text: 'Get notified as soon as your application is reviewed' },
    ],
    submit: 'Apply to deliver',
  },
  ADMIN: {
    label: 'Admin',
    icon: 'bi-shield-check',
    title: 'Admin',
    tagline: 'Platform team, access code needed',
    needs: ['Admin access code from the ShopSphere team'],
    loginHint: 'Manage categories, products, sellers, orders, payments and settings. New admins register with the team access code.',
    asideTitle: 'Run the marketplace.',
    asideText: 'Admins manage categories, products, sellers, orders, payments and every platform setting.',
    image: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=1400&q=80',
    points: [
      { icon: 'bi-key', text: 'Registration needs the secret admin access code' },
      { icon: 'bi-speedometer2', text: 'Full admin console: categories, products, users, orders, reports' },
      { icon: 'bi-bell', text: 'Every admin is notified when a new admin registers' },
    ],
    submit: 'Create admin account',
  },
}

/** Roles a visitor can choose on the Register page. */
export const REGISTER_ROLES = ['CUSTOMER', 'SELLER', 'DELIVERY_PARTNER', 'ADMIN']

/** Tabs on the Login page. Signing in works the same for all; the tab only sets expectations. */
export const LOGIN_ROLES = ['CUSTOMER', 'SELLER', 'DELIVERY_PARTNER', 'ADMIN']

export const BUSINESS_TYPES = [
  { value: 'INDIVIDUAL', label: 'Individual', hint: 'Selling in your own name' },
  { value: 'PROPRIETORSHIP', label: 'Proprietorship', hint: 'Registered sole business' },
  { value: 'PARTNERSHIP', label: 'Partnership / LLP', hint: 'Two or more partners' },
  { value: 'PRIVATE_LIMITED', label: 'Private limited', hint: 'Registered company' },
]

export const VEHICLE_TYPES = [
  { value: 'BICYCLE', label: 'Bicycle', icon: 'bi-bicycle' },
  { value: 'MOTORCYCLE', label: 'Motorcycle', icon: 'bi-scooter' },
  { value: 'SCOOTER', label: 'Scooter', icon: 'bi-scooter' },
  { value: 'ELECTRIC_SCOOTER', label: 'Electric scooter', icon: 'bi-lightning-charge' },
]

export const GSTIN_RE = /^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/
/** Indian registration (MH12AB1234) or BH series (22BH1234AA); spaces and dashes ignored. */
export const VEHICLE_NUMBER_RE = /^([A-Z]{2}\d{1,2}[A-Z]{0,3}\d{4}|\d{2}BH\d{4}[A-Z]{1,2})$/
export const normaliseVehicleNumber = (v) => v.replace(/[\s-]/g, '').toUpperCase()
