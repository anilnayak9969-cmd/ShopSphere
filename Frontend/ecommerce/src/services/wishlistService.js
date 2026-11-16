import api from './api'

const wishlistService = {
  getWishlist: () => api.get('/customer/wishlist').then((r) => r.data),
  add: (productId) => api.post(`/customer/wishlist/${productId}`).then((r) => r.data),
  remove: (productId) => api.delete(`/customer/wishlist/${productId}`).then((r) => r.data),
  /** Adds 1 unit to the cart (stock checked) and removes it from the wishlist in one server step. */
  moveToCart: (productId) => api.post(`/customer/wishlist/${productId}/move-to-cart`).then((r) => r.data),
}

export default wishlistService
