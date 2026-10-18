import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useAuth } from './AuthContext'
import { useToast } from './ToastContext'
import { connectLive } from '../services/liveService'

const LiveContext = createContext({ connected: false, subscribe: () => () => {} })

/**
 * One live connection per tab while signed in. Pages subscribe with useLiveEvent(handler) to refresh
 * their data the moment something changes; every new notification also pops up as a toast.
 */
export function LiveProvider({ children }) {
  const { user } = useAuth()
  const toast = useToast()
  const [connected, setConnected] = useState(false)
  const listeners = useRef(new Set())
  const toastRef = useRef(toast)
  useEffect(() => {
    toastRef.current = toast
  }, [toast])

  useEffect(() => {
    if (!user) return undefined
    const close = connectLive({
      onStatus: setConnected,
      onEvent: (type, data) => {
        if (type === 'notification' && data?.title) {
          toastRef.current.info(`${data.title} — ${data.message}`)
        }
        listeners.current.forEach((fn) => fn(type, data))
      },
    })
    return () => {
      close()
      setConnected(false)
    }
  }, [user?.id]) // eslint-disable-line react-hooks/exhaustive-deps

  const subscribe = useCallback((fn) => {
    listeners.current.add(fn)
    return () => listeners.current.delete(fn)
  }, [])
  const value = useMemo(() => ({ connected, subscribe }), [connected, subscribe])

  return <LiveContext.Provider value={value}>{children}</LiveContext.Provider>
}

export const useLiveStatus = () => useContext(LiveContext).connected

/** Calls handler(type, data) for every live event while the component is mounted. */
export function useLiveEvent(handler) {
  const { subscribe } = useContext(LiveContext)
  const ref = useRef(handler)
  useEffect(() => {
    ref.current = handler
  })
  useEffect(() => subscribe((type, data) => ref.current(type, data)), [subscribe])
}
