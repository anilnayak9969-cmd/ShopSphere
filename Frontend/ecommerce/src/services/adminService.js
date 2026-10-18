import api, { cleanParams } from './api'

const adminService = {
  getDashboard: () => api.get('/admin/dashboard').then((r) => r.data),

  // Category Management
  getCategories: (params) => api.get('/admin/categories', { params: cleanParams(params) }).then((r) => r.data),
  getCategory: (id) => api.get(`/admin/categories/${id}`).then((r) => r.data),
  createCategory: (payload) => api.post('/admin/categories', payload).then((r) => r.data),
  updateCategory: (id, payload) => api.put(`/admin/categories/${id}`, payload).then((r) => r.data),
  setCategoryActive: (id, active) => api.patch(`/admin/categories/${id}/status`, { active }).then((r) => r.data),
  deleteCategory: (id) => api.delete(`/admin/categories/${id}`).then((r) => r.data),
  reassignProducts: (id, targetCategoryId) =>
    api.post(`/admin/categories/${id}/reassign-products`, { targetCategoryId }).then((r) => r.data),

  // Users & sellers
  getUsers: (params) => api.get('/admin/users', { params: cleanParams(params) }).then((r) => r.data),
  setSellerStatus: (id, status) => api.patch(`/admin/sellers/${id}/status`, { status }).then((r) => r.data),
  setUserActive: (id, active) => api.patch(`/admin/users/${id}/status`, { active }).then((r) => r.data),
  /** { fullName, email, phone, password } — only admins can create admins. */
  createAdmin: (payload) => api.post('/admin/admins', payload).then((r) => r.data),

  // Delivery partner applications
  getDeliveryPartners: (params) => api.get('/admin/delivery-partners', { params: cleanParams(params) }).then((r) => r.data),
  setDeliveryPartnerStatus: (id, status) => api.patch(`/admin/delivery-partners/${id}/status`, { status }).then((r) => r.data),

  // Products & approval queue
  getProducts: (params) => api.get('/admin/products', { params: cleanParams(params) }).then((r) => r.data),
  setProductActive: (id, active) => api.patch(`/admin/products/${id}/status`, { active }).then((r) => r.data),
  approveProduct: (id) => api.patch(`/admin/products/${id}/approve`).then((r) => r.data),
  rejectProduct: (id, reason) => api.patch(`/admin/products/${id}/reject`, { reason }).then((r) => r.data),

  // Orders, payments, returns
  getOrders: (params) => api.get('/admin/orders', { params: cleanParams(params) }).then((r) => r.data),
  getOrder: (id) => api.get(`/admin/orders/${id}`).then((r) => r.data),
  setOrderStatus: (id, status, note) => api.patch(`/admin/orders/${id}/status`, { status, note }).then((r) => r.data),
  getPayments: (params) => api.get('/admin/payments', { params: cleanParams(params) }).then((r) => r.data),
  getReturns: (params) => api.get('/admin/returns', { params: cleanParams(params) }).then((r) => r.data),
  decideReturn: (id, approve, note) => api.patch(`/admin/returns/${id}`, { approve, note }).then((r) => r.data),

  // Coupons
  getCoupons: (params) => api.get('/admin/coupons', { params: cleanParams(params) }).then((r) => r.data),
  createCoupon: (payload) => api.post('/admin/coupons', payload).then((r) => r.data),
  updateCoupon: (id, payload) => api.put(`/admin/coupons/${id}`, payload).then((r) => r.data),
  setCouponActive: (id, active) => api.patch(`/admin/coupons/${id}/status`, { active }).then((r) => r.data),

  // Reviews
  getReviews: (params) => api.get('/admin/reviews', { params: cleanParams(params) }).then((r) => r.data),
  deleteReview: (id) => api.delete(`/admin/reviews/${id}`).then((r) => r.data),

  // Reports, settings, messages
  getReports: (days = 30) => api.get('/admin/reports', { params: { days } }).then((r) => r.data),
  getSettings: () => api.get('/admin/settings').then((r) => r.data),
  updateSettings: (payload) => api.put('/admin/settings', payload).then((r) => r.data),
  getMessages: (params) => api.get('/admin/messages', { params: cleanParams(params) }).then((r) => r.data),
  setMessageResolved: (id, resolved) => api.patch(`/admin/messages/${id}`, { active: resolved }).then((r) => r.data),
}

export default adminService
