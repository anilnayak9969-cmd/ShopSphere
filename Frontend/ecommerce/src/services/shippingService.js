import api from './api'

/** Shipping Management (customer): delivery prices for the cart, and shipment tracking. */
const shippingService = {
  /** { city, state, pincode, couponCode } → { zone, zoneLabel, weightKg, subtotal, discount, options[] } */
  quote: (payload) => api.post('/customer/shipping/quote', payload).then((r) => r.data),
  track: (trackingNumber) => api.get(`/customer/track/${encodeURIComponent(trackingNumber.trim())}`).then((r) => r.data),
}

export default shippingService
