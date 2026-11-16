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

  // Customer Management (dashboard, add, update, deactivate)
  getCustomers: (params) => api.get('/admin/customers', { params: cleanParams(params) }).then((r) => r.data),
  getCustomerSummary: () => api.get('/admin/customers/summary').then((r) => r.data),
  /** { customer, addresses, recentOrders } */
  getCustomer: (id) => api.get(`/admin/customers/${id}`).then((r) => r.data),
  /** { fullName, email, phone, password } */
  createCustomer: (payload) => api.post('/admin/customers', payload).then((r) => r.data),
  /** { fullName, email, phone, active } */
  updateCustomer: (id, payload) => api.put(`/admin/customers/${id}`, payload).then((r) => r.data),
  setCustomerActive: (id, active) => api.patch(`/admin/customers/${id}/status`, { active }).then((r) => r.data),

  // Users & sellers
  getUsers: (params) => api.get('/admin/users', { params: cleanParams(params) }).then((r) => r.data),
  setSellerStatus: (id, status) => api.patch(`/admin/sellers/${id}/status`, { status }).then((r) => r.data),
  setUserActive: (id, active) => api.patch(`/admin/users/${id}/status`, { active }).then((r) => r.data),
  /** { fullName, email, phone, password } — only admins can create admins. */
  createAdmin: (payload) => api.post('/admin/admins', payload).then((r) => r.data),

  // Delivery partner applications
  getDeliveryPartners: (params) => api.get('/admin/delivery-partners', { params: cleanParams(params) }).then((r) => r.data),
  setDeliveryPartnerStatus: (id, status) => api.patch(`/admin/delivery-partners/${id}/status`, { status }).then((r) => r.data),

  // Product Management (dashboard, add, update, deactivate) & approval queue
  getProducts: (params) => api.get('/admin/products', { params: cleanParams(params) }).then((r) => r.data),
  getProductSummary: () => api.get('/admin/products/summary').then((r) => r.data),
  getProduct: (id) => api.get(`/admin/products/${id}`).then((r) => r.data),
  /** { sellerId, product: { productName, description, sku, price, categoryId, stockQuantity, ... } } */
  createProduct: (payload) => api.post('/admin/products', payload).then((r) => r.data),
  updateProduct: (id, payload) => api.put(`/admin/products/${id}`, payload).then((r) => r.data),
  /** active=false is the soft delete (deactivate); true reactivates. */
  setProductActive: (id, active) => api.patch(`/admin/products/${id}/status`, { active }).then((r) => r.data),
  approveProduct: (id) => api.patch(`/admin/products/${id}/approve`).then((r) => r.data),
  moveProduct: (id, categoryId) => api.patch(`/admin/products/${id}/category`, { categoryId }).then((r) => r.data),
  rejectProduct: (id, reason) => api.patch(`/admin/products/${id}/reject`, { reason }).then((r) => r.data),

  // Order Management (dashboard, status updates, cancel), payments, returns
  /** params: { group: PENDING|SHIPPED|DELIVERED|CANCELLED|RETURNS|UNPAID, status, search, page, size } */
  getOrders: (params) => api.get('/admin/orders', { params: cleanParams(params) }).then((r) => r.data),
  getOrderSummary: () => api.get('/admin/orders/summary').then((r) => r.data),
  getOrder: (id) => api.get(`/admin/orders/${id}`).then((r) => r.data),
  setOrderStatus: (id, status, note) => api.patch(`/admin/orders/${id}/status`, { status, note }).then((r) => r.data),
  // Payment Management: params { status, refundedOnly, method: RAZORPAY|COD, search, page, size }
  getPayments: (params) => api.get('/admin/payments', { params: cleanParams(params) }).then((r) => r.data),
  getPaymentSummary: () => api.get('/admin/payments/summary').then((r) => r.data),
  getRefunds: (params) => api.get('/admin/refunds', { params: cleanParams(params) }).then((r) => r.data),
  getPaymentRefunds: (id) => api.get(`/admin/payments/${id}/refunds`).then((r) => r.data),
  /** { amount, reason } — only for cancelled or returned orders */
  issueRefund: (id, payload) => api.post(`/admin/payments/${id}/refund`, payload).then((r) => r.data),
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
