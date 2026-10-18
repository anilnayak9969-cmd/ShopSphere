import api, { cleanParams } from './api'

const sellerService = {
  getDashboard: () => api.get('/seller/dashboard').then((r) => r.data),

  getProfile: () => api.get('/seller/profile').then((r) => r.data),
  updateProfile: (payload) => api.put('/seller/profile', payload).then((r) => r.data),

  /** params: search, approval, maxStock (inventory view), page, size */
  getProducts: (params) => api.get('/seller/products', { params: cleanParams(params) }).then((r) => r.data),
  getProduct: (id) => api.get(`/seller/products/${id}`).then((r) => r.data),
  createProduct: (payload) => api.post('/seller/products', payload).then((r) => r.data),
  updateProduct: (id, payload) => api.put(`/seller/products/${id}`, payload).then((r) => r.data),
  setProductActive: (id, active) => api.patch(`/seller/products/${id}/status`, { active }).then((r) => r.data),
  updateStock: (id, stockQuantity) => api.patch(`/seller/products/${id}/stock`, { stockQuantity }).then((r) => r.data),

  getOrders: (params) => api.get('/seller/orders', { params: cleanParams(params) }).then((r) => r.data),
  /** { status, courierName?, trackingNumber?, note? } */
  updateOrderItem: (id, payload) => api.patch(`/seller/order-items/${id}/status`, payload).then((r) => r.data),

  getReturns: (params) => api.get('/seller/returns', { params: cleanParams(params) }).then((r) => r.data),
  decideReturn: (id, approve, note) => api.patch(`/seller/returns/${id}`, { approve, note }).then((r) => r.data),

  getSalesReport: (days = 30) => api.get('/seller/reports/sales', { params: { days } }).then((r) => r.data),
  getCommission: () => api.get('/seller/reports/commission').then((r) => r.data),
}

export default sellerService
