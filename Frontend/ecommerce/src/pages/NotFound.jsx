import { Link } from 'react-router-dom'
import { EmptyState } from '../components/ui'

export default function NotFound() {
  return (
    <div className="container ss-page">
      <EmptyState
        icon="bi-compass"
        title="We couldn't find that page"
        action={
          <Link to="/" className="btn btn-ink">
            Back to home
          </Link>
        }
      >
        The link may be broken, or the page may have moved.
      </EmptyState>
    </div>
  )
}
