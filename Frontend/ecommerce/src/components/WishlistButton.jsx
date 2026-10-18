import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useWishlist } from '../context/WishlistContext'
import { getErrorMessage } from '../utils/errors'

/** Heart toggle. Guests are sent to sign in; sellers and admins don't see it at all. */
export default function WishlistButton({ product, variant = 'overlay' }) {
  const { user } = useAuth()
  const { has, toggle } = useWishlist()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const [busy, setBusy] = useState(false)

  if (user && user.role !== 'CUSTOMER') return null
  const saved = has(product.productId)

  const onClick = async (event) => {
    event.preventDefault()
    event.stopPropagation()
    if (!user) {
      navigate('/login', { state: { from: location } })
      return
    }
    setBusy(true)
    try {
      const nowSaved = await toggle(product.productId)
      toast.success(nowSaved ? 'Saved to your wishlist' : 'Removed from your wishlist')
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const label = saved ? `Remove ${product.productName} from wishlist` : `Save ${product.productName} to wishlist`

  if (variant === 'button') {
    return (
      <button type="button" className={`btn btn-ghost btn-lg ${saved ? 'ss-wish-on' : ''}`} onClick={onClick} disabled={busy} aria-pressed={saved} aria-label={label}>
        <i className={`bi ${saved ? 'bi-heart-fill' : 'bi-heart'} me-2`} />
        {saved ? 'Saved' : 'Save'}
      </button>
    )
  }

  return (
    <button type="button" className={`ss-wish ${saved ? 'on' : ''}`} onClick={onClick} disabled={busy} aria-pressed={saved} aria-label={label}>
      <i className={`bi ${saved ? 'bi-heart-fill' : 'bi-heart'}`} />
    </button>
  )
}
