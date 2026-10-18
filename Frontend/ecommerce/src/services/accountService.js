import api from './api'

/** Customer address book and the customer's own reviews. */
const accountService = {
  getAddresses: () => api.get('/customer/addresses').then((r) => r.data),
  createAddress: (payload) => api.post('/customer/addresses', payload).then((r) => r.data),
  updateAddress: (id, payload) => api.put(`/customer/addresses/${id}`, payload).then((r) => r.data),
  makeDefaultAddress: (id) => api.patch(`/customer/addresses/${id}/default`).then((r) => r.data),
  deleteAddress: (id) => api.delete(`/customer/addresses/${id}`).then((r) => r.data),

  getMyReviews: (page = 0, size = 10) => api.get('/customer/reviews', { params: { page, size } }).then((r) => r.data),
  getReviewEligibility: (productId) => api.get(`/customer/products/${productId}/review`).then((r) => r.data),
  saveReview: (productId, payload) => api.put(`/customer/products/${productId}/review`, payload).then((r) => r.data),
  deleteReview: (productId) => api.delete(`/customer/products/${productId}/review`).then((r) => r.data),
}

export default accountService
