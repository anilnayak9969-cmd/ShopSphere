import { Alert } from '../../components/ui'

const MESSAGES = {
  PENDING: {
    variant: 'warning',
    icon: 'bi-hourglass-split',
    text: 'Your store is under review. You can prepare your catalog, and you will be able to publish products as soon as an administrator approves your store.',
  },
  REJECTED: {
    variant: 'danger',
    text: 'Your seller application was not approved. Please contact the ShopSphere team for details.',
  },
  SUSPENDED: {
    variant: 'danger',
    text: 'Your store is suspended. Your products are hidden from customers until the suspension is lifted.',
  },
}

export default function SellerStatusBanner({ status }) {
  const message = MESSAGES[status]
  if (!message) return null
  return (
    <Alert variant={message.variant} icon={message.icon} className="mb-4">
      {message.text}
    </Alert>
  )
}
