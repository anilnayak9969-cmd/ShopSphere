import api from './api'

const orderService = {
  /** RAZORPAY returns a Razorpay order to open Checkout; COD places the order immediately. */
  checkout: (payload) => api.post('/customer/checkout', payload).then((r) => r.data),
  getOrders: (page = 0, size = 10) => api.get('/customer/orders', { params: { page, size } }).then((r) => r.data),
  getOrder: (id) => api.get(`/customer/orders/${id}`).then((r) => r.data),
  cancelOrder: (id) => api.post(`/customer/orders/${id}/cancel`).then((r) => r.data),
  requestReturn: (orderItemId, reason, comments) =>
    api.post('/customer/returns', { orderItemId, reason, comments }).then((r) => r.data),
  getReturns: (page = 0, size = 10) => api.get('/customer/returns', { params: { page, size } }).then((r) => r.data),
}

export default orderService
