import api from './api'

/** Data anyone can read, plus the public contact form. */
const publicService = {
  /** freeShippingThreshold, shippingFee, codEnabled, codMaxAmount, returnWindowDays */
  getSettings: () => api.get('/public/settings').then((r) => r.data),
  /** Coupons that are valid right now (used for promotional banners). */
  getActiveCoupons: () => api.get('/public/coupons').then((r) => r.data),
  sendContactMessage: (payload) => api.post('/contact', payload).then((r) => r.data),
  /** Demo logins for the sign-in page; empty unless the backend runs with demo data. */
  getDemoAccounts: () => api.get('/public/demo-accounts').then((r) => r.data),
}

export default publicService
