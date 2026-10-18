import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import authService from '../services/authService'
import { tokenStorage } from '../services/api'

const AuthContext = createContext(null)

/** Where each role lands after signing in. */
export function homePathFor(role) {
  if (role === 'ADMIN') return '/admin'
  if (role === 'SELLER') return '/seller'
  if (role === 'DELIVERY_PARTNER') return '/delivery'
  return '/'
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  // True while we check a saved token on page load, so protected pages don't flash the login screen.
  const [initializing, setInitializing] = useState(() => Boolean(tokenStorage.get()))

  useEffect(() => {
    if (!tokenStorage.get()) return
    authService
      .me()
      .then(setUser)
      .catch(() => {
        tokenStorage.clear()
        setUser(null)
      })
      .finally(() => setInitializing(false))
  }, [])

  useEffect(() => {
    const handleExpired = () => setUser(null)
    window.addEventListener('auth:expired', handleExpired)
    return () => window.removeEventListener('auth:expired', handleExpired)
  }, [])

  /** Stores the token from any AuthResponse (login or OTP verification). */
  const completeLogin = useCallback((authResponse) => {
    tokenStorage.set(authResponse.token)
    setUser(authResponse.user)
    return authResponse.user
  }, [])

  const login = useCallback(
    async (email, password) => completeLogin(await authService.login(email, password)),
    [completeLogin],
  )

  const logout = useCallback(() => {
    tokenStorage.clear()
    setUser(null)
  }, [])

  const refreshUser = useCallback(async () => {
    const fresh = await authService.me()
    setUser(fresh)
    return fresh
  }, [])

  const value = useMemo(
    () => ({ user, initializing, login, logout, completeLogin, refreshUser }),
    [user, initializing, login, logout, completeLogin, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
