import { API_BASE_URL, tokenStorage } from './api'

/**
 * Opens the live event stream (Server-Sent Events) for the signed-in user.
 * fetch() is used instead of EventSource so the JWT goes in the Authorization header, not the URL.
 * Reconnects automatically with back-off; returns a function that closes the stream for good.
 *
 * onEvent(type, data) — e.g. ('notification', {title, message, link}) or ('pickups', {...})
 * onStatus(connected)
 */
export function connectLive({ onEvent, onStatus }) {
  let stopped = false
  let controller = null
  let retryMs = 2000
  let timer = null

  const dispatch = (block) => {
    let type = 'message'
    const data = []
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) continue // comment / heartbeat
      const i = line.indexOf(':')
      const field = i === -1 ? line : line.slice(0, i)
      const value = i === -1 ? '' : line.slice(i + 1).replace(/^ /, '')
      if (field === 'event') type = value
      else if (field === 'data') data.push(value)
    }
    if (!data.length) return
    let payload = data.join('\n')
    try {
      payload = JSON.parse(payload)
    } catch {
      /* plain text event */
    }
    onEvent(type, payload)
  }

  const open = async () => {
    const token = tokenStorage.get()
    if (stopped || !token) return
    controller = new AbortController()
    try {
      const res = await fetch(`${API_BASE_URL}/live/stream`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal: controller.signal,
        cache: 'no-store',
      })
      if (res.status === 401 || res.status === 403) {
        onStatus(false)
        return // signed out or not allowed: do not retry
      }
      if (!res.ok || !res.body) throw new Error(`stream ${res.status}`)
      onStatus(true)
      retryMs = 2000
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      for (;;) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
        let split
        while ((split = buffer.indexOf('\n\n')) !== -1) {
          dispatch(buffer.slice(0, split))
          buffer = buffer.slice(split + 2)
        }
      }
    } catch {
      /* network error or aborted — fall through to reconnect */
    }
    onStatus(false)
    if (!stopped) {
      timer = setTimeout(open, retryMs)
      retryMs = Math.min(retryMs * 2, 30000)
    }
  }

  open()
  return () => {
    stopped = true
    clearTimeout(timer)
    controller?.abort()
  }
}
