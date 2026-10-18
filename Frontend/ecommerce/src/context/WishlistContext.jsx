import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import wishlistService from '../services/wishlistService'
import { useAuth } from './AuthContext'

const WishlistContext = createContext(null)

/** Keeps the saved-for-later list in sync, so every heart icon on the site shows the right state. */
export function WishlistProvider({ children }) {
  const { user } = useAuth()
  const isCustomer = user?.role === 'CUSTOMER'
  const [items, setItems] = useState(null)

  const refresh = useCallback(async () => {
    if (!isCustomer) {
      setItems(null)
      return
    }
    try {
      setItems(await wishlistService.getWishlist())
    } catch {
      setItems([])
    }
  }, [isCustomer])

  useEffect(() => {
    refresh()
  }, [refresh])

  const ids = useMemo(() => new Set((items || []).map((i) => i.product.productId)), [items])

  const toggle = useCallback(
    async (productId) => {
      const updated = ids.has(productId)
        ? await wishlistService.remove(productId)
        : await wishlistService.add(productId)
      setItems(updated)
      return !ids.has(productId)
    },
    [ids],
  )

  const remove = useCallback(async (productId) => {
    setItems(await wishlistService.remove(productId))
  }, [])

  const value = useMemo(
    () => ({ items, ids, count: ids.size, isCustomer, refresh, toggle, remove, has: (id) => ids.has(id) }),
    [items, ids, isCustomer, refresh, toggle, remove],
  )

  return <WishlistContext.Provider value={value}>{children}</WishlistContext.Provider>
}

export const useWishlist = () => useContext(WishlistContext)
