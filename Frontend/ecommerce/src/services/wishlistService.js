import api from './api'

const wishlistService = {
  getWishlist: () => api.get('/customer/wishlist').then((r) => r.data),
  add: (productId) => api.post(`/customer/wishlist/${productId}`).then((r) => r.data),
  remove: (productId) => api.delete(`/customer/wishlist/${productId}`).then((r) => r.data),
}

export default wishlistService
