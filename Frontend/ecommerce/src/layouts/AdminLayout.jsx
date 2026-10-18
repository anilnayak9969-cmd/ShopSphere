import DashboardLayout from './DashboardLayout'

const LINKS = [
  { to: '/admin', label: 'Overview', icon: 'bi-speedometer2', end: true },
  { to: '/admin/users', label: 'Users', icon: 'bi-people' },
  { to: '/admin/sellers', label: 'Sellers', icon: 'bi-shop' },
  { to: '/admin/delivery-partners', label: 'Delivery partners', icon: 'bi-bicycle' },
  { to: '/admin/categories', label: 'Categories', icon: 'bi-tags' },
  { to: '/admin/products', label: 'Products', icon: 'bi-box-seam' },
  { to: '/admin/orders', label: 'Orders', icon: 'bi-receipt' },
  { to: '/admin/payments', label: 'Payments & refunds', icon: 'bi-credit-card' },
  { to: '/admin/returns', label: 'Returns', icon: 'bi-arrow-counterclockwise' },
  { to: '/admin/coupons', label: 'Coupons', icon: 'bi-ticket-perforated' },
  { to: '/admin/reviews', label: 'Reviews', icon: 'bi-chat-square-quote' },
  { to: '/admin/reports', label: 'Reports', icon: 'bi-bar-chart-line' },
  { to: '/admin/messages', label: 'Messages', icon: 'bi-envelope' },
  { to: '/admin/settings', label: 'Settings', icon: 'bi-gear' },
]

export default function AdminLayout() {
  return <DashboardLayout sectionLabel="Admin console" links={LINKS} />
}
