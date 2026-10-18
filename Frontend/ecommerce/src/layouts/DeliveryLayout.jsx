import DashboardLayout from './DashboardLayout'

const LINKS = [
  { to: '/delivery', label: 'Deliveries', icon: 'bi-truck', end: true },
  { to: '/delivery/profile', label: 'Profile', icon: 'bi-person-badge' },
  { to: '/delivery/notifications', label: 'Notifications', icon: 'bi-bell' },
]

export default function DeliveryLayout() {
  return <DashboardLayout sectionLabel="Delivery partner" links={LINKS} />
}
