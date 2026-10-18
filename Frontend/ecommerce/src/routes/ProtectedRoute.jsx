import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { homePathFor, useAuth } from '../context/AuthContext'
import { PageLoader } from '../components/ui'

/**
 * Hides a page unless the user is signed in (and has one of `roles`, when given).
 * This only improves the experience — the backend independently rejects unauthorised calls.
 */
export default function ProtectedRoute({ roles, children }) {
  const { user, initializing } = useAuth()
  const location = useLocation()

  if (initializing) return <PageLoader label="Restoring your session…" />
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />
  if (roles && !roles.includes(user.role)) return <Navigate to={homePathFor(user.role)} replace />
  return children ?? <Outlet />
}

/** Login/register pages: already signed-in users are sent to their home page. */
export function GuestRoute({ children }) {
  const { user, initializing } = useAuth()
  if (initializing) return <PageLoader />
  if (user) return <Navigate to={homePathFor(user.role)} replace />
  return children ?? <Outlet />
}
