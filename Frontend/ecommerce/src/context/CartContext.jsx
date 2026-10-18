import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import cartService from '../services/cartService'
import { useAuth } from './AuthContext'

const CartContext = createContext(null)

/**
 * The cart lives in the database (one per customer). This context keeps the latest copy
 * returned by the API so the header badge and cart page stay in sync.
 */
export function CartProvider({ children }) {
  const { user } = useAuth()
  const isCustomer = user?.role === 'CUSTOMER'
  const [cart, setCart] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const refresh = useCallback(async () => {
    if (!isCustomer) {
      setCart(null)
      return null
    }
    setLoading(true)
    setError(null)
    try {
      const data = await cartService.getCart()
      setCart(data)
      return data
    } catch (err) {
      setError(err)
      return null
    } finally {
      setLoading(false)
    }
  }, [isCustomer])

  useEffect(() => {
    refresh()
  }, [refresh])

  // Each mutation returns the full updated cart from the server, so we never guess totals here.
  const addItem = useCallback(async (productId, quantity = 1) => {
    const data = await cartService.addItem(productId, quantity)
    setCart(data)
    return data
  }, [])

  const updateItem = useCallback(async (itemId, quantity) => {
    const data = await cartService.updateItem(itemId, quantity)
    setCart(data)
    return data
  }, [])

  const removeItem = useCallback(async (itemId) => {
    const data = await cartService.removeItem(itemId)
    setCart(data)
    return data
  }, [])

  const saveForLater = useCallback(async (itemId) => {
    const data = await cartService.saveForLater(itemId)
    setCart(data)
    return data
  }, [])

  const acknowledgePrices = useCallback(async () => {
    const data = await cartService.acknowledgePrices()
    setCart(data)
    return data
  }, [])

  const count = cart?.items?.reduce((sum, item) => sum + item.quantity, 0) ?? 0

  const value = useMemo(
    () => ({ cart, count, loading, error, isCustomer, refresh, addItem, updateItem, removeItem, saveForLater, acknowledgePrices }),
    [cart, count, loading, error, isCustomer, refresh, addItem, updateItem, removeItem, saveForLater, acknowledgePrices],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export const useCart = () => useContext(CartContext)
