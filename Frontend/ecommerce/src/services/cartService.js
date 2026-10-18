import api from './api'

const cartService = {
  getCart: () => api.get('/customer/cart').then((r) => r.data),
  addItem: (productId, quantity) => api.post('/customer/cart/items', { productId, quantity }).then((r) => r.data),
  updateItem: (itemId, quantity) => api.put(`/customer/cart/items/${itemId}`, { quantity }).then((r) => r.data),
  removeItem: (itemId) => api.delete(`/customer/cart/items/${itemId}`).then((r) => r.data),
  saveForLater: (itemId) => api.post(`/customer/cart/items/${itemId}/save-for-later`).then((r) => r.data),
  acknowledgePrices: () => api.post('/customer/cart/acknowledge-prices').then((r) => r.data),
  /** Previews a coupon on the current cart. Checkout validates it again on the server. */
  applyCoupon: (code) => api.post('/customer/coupons/apply', { code }).then((r) => r.data),
}

export default cartService
