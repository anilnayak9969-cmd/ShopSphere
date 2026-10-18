import axios from 'axios'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

/** Origin of the backend (e.g. http://localhost:8080) — used to show uploaded images (/api/images/...). */
export const API_ORIGIN = new URL(API_BASE_URL).origin

const TOKEN_KEY = 'shopsphere.token'

/**
 * Where the JWT lives:
 * - sessionStorage holds the token of *this tab*, so signing in as a seller in another tab never
 *   swaps the account this tab is using (that used to cause "You do not have permission" errors).
 * - localStorage remembers the latest sign-in, so a new tab or a refresh starts signed in.
 * Trade-off: any injected script could read it, so the app never renders raw HTML
 * (React escapes everything) and tokens expire after one day.
 * Every access is wrapped in try/catch because storage can be blocked (private mode).
 */
const safe = (fn, fallback = null) => {
  try {
    return fn()
  } catch {
    return fallback
  }
}

export const tokenStorage = {
  get() {
    const own = safe(() => sessionStorage.getItem(TOKEN_KEY))
    if (own) return own
    // First request in a new tab: adopt the latest sign-in and keep it for this tab only.
    const latest = safe(() => localStorage.getItem(TOKEN_KEY))
    if (latest) safe(() => sessionStorage.setItem(TOKEN_KEY, latest))
    return latest
  },
  set(token) {
    safe(() => sessionStorage.setItem(TOKEN_KEY, token))
    safe(() => localStorage.setItem(TOKEN_KEY, token))
  },
  clear() {
    const own = safe(() => sessionStorage.getItem(TOKEN_KEY))
    safe(() => sessionStorage.removeItem(TOKEN_KEY))
    // Only forget the remembered sign-in if it is this tab's account.
    if (!own || safe(() => localStorage.getItem(TOKEN_KEY)) === own) safe(() => localStorage.removeItem(TOKEN_KEY))
  },
}

/** Removes empty values so URLs only contain the filters the user actually chose. */
export function cleanParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(
      ([, value]) => value !== undefined && value !== null && value !== '' && !(Array.isArray(value) && value.length === 0),
    ),
  )
}

/** Turns relative upload paths (/api/images/x.jpg) into full URLs; leaves http(s) URLs as they are. */
export function resolveImageUrl(src) {
  if (!src) return src
  return src.startsWith('/api/') ? `${API_ORIGIN}${src}` : src
}

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
  // Repeated params as brand=A&brand=B (what Spring expects for List<String>).
  paramsSerializer: { indexes: null },
})

// Attach "Authorization: Bearer <token>" to every request when signed in.
api.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// A 401 while we hold a token means it expired or the account was suspended: sign out.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && tokenStorage.get()) {
      tokenStorage.clear()
      window.dispatchEvent(new Event('auth:expired'))
    }
    return Promise.reject(error)
  },
)

export default api
