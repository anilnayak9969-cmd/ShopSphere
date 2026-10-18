import DashboardLayout from './DashboardLayout'

const LINKS = [
  { to: '/seller', label: 'Overview', icon: 'bi-grid-1x2', end: true },
  { to: '/seller/products', label: 'Products', icon: 'bi-box-seam', end: true },
  { to: '/seller/products/new', label: 'Add product', icon: 'bi-plus-square', end: true },
  { to: '/seller/inventory', label: 'Inventory', icon: 'bi-boxes' },
  { to: '/seller/orders', label: 'Orders', icon: 'bi-receipt' },
  { to: '/seller/returns', label: 'Returns', icon: 'bi-arrow-counterclockwise' },
  { to: '/seller/sales', label: 'Sales reports', icon: 'bi-graph-up-arrow' },
  { to: '/seller/commission', label: 'Commission', icon: 'bi-percent' },
  { to: '/seller/profile', label: 'Store profile', icon: 'bi-shop' },
  { to: '/seller/notifications', label: 'Notifications', icon: 'bi-bell' },
]

export default function SellerLayout() {
  return <DashboardLayout sectionLabel="Seller studio" links={LINKS} />
}
