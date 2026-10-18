// These mirror the backend's Bean Validation rules so users see errors before submitting.
// The backend still validates everything — client checks are only for convenience.

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
export const PHONE_RE = /^[6-9]\d{9}$/
export const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,64}$/
export const PINCODE_RE = /^[1-9]\d{5}$/
export const URL_RE = /^https?:\/\/\S+$/
/** An http(s) link or an image uploaded to our server (/api/images/<id>.jpg). */
export const IMAGE_URL_RE = /^(https?:\/\/\S+|\/api\/images\/[A-Za-z0-9._-]+)$/
export const SKU_RE = /^[A-Za-z0-9_-]{3,60}$/

export const PASSWORD_RULES = [
  { label: '8+ characters', test: (v) => v.length >= 8 },
  { label: 'Uppercase letter', test: (v) => /[A-Z]/.test(v) },
  { label: 'Lowercase letter', test: (v) => /[a-z]/.test(v) },
  { label: 'Number', test: (v) => /\d/.test(v) },
]

export const PASSWORD_MESSAGE =
  'Password must be 8-64 characters and include an uppercase letter, a lowercase letter and a number'
