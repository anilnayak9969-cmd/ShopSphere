import api from './api'

/** Delivery partner's own account and parcels (DELIVERY_PARTNER role). */
const deliveryService = {
  getProfile: () => api.get('/delivery/profile').then((r) => r.data),
  /** { vehicleType, vehicleNumber, serviceCity, servicePincode } */
  updateProfile: (payload) => api.put('/delivery/profile', payload).then((r) => r.data),

  /** { partner, availablePickups, activeDeliveries, deliveredToday, deliveredTotal } */
  getDashboard: () => api.get('/delivery/dashboard').then((r) => r.data),
  /** Packed parcels waiting for a partner. */
  getPickups: (page = 0, size = 10) => api.get('/delivery/pickups', { params: { page, size } }).then((r) => r.data),
  /** state: 'active' (on the way) or 'completed' (delivered). */
  getJobs: (state, page = 0, size = 10) => api.get('/delivery/jobs', { params: { state, page, size } }).then((r) => r.data),
  accept: (orderItemId) => api.post(`/delivery/jobs/${orderItemId}/accept`).then((r) => r.data),
  /** status: OUT_FOR_DELIVERY or DELIVERED */
  updateStatus: (orderItemId, status) => api.patch(`/delivery/jobs/${orderItemId}/status`, { status }).then((r) => r.data),
}

export default deliveryService
