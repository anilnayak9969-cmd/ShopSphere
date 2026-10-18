import api from './api'

const paymentService = {
  /** Sends Razorpay's signature to the server — only the server decides if a payment is genuine. */
  verifyPayment: (payload) => api.post('/customer/payments/verify', payload).then((r) => r.data),
  paymentFailed: (razorpayOrderId, reason) =>
    api.post('/customer/payments/failed', { razorpayOrderId, reason }).then((r) => r.data),
  getHistory: (page = 0, size = 10) => api.get('/customer/payments', { params: { page, size } }).then((r) => r.data),
}

export default paymentService
