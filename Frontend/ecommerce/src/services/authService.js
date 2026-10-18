import api from './api'

const authService = {
  register: (payload) => api.post('/auth/register', payload).then((r) => r.data),
  verifyOtp: (email, otp) => api.post('/auth/verify-otp', { email, otp }).then((r) => r.data),
  resendOtp: (email, purpose) => api.post('/auth/resend-otp', { email, purpose }).then((r) => r.data),
  login: (email, password) => api.post('/auth/login', { email, password }).then((r) => r.data),
  forgotPassword: (email) => api.post('/auth/forgot-password', { email }).then((r) => r.data),
  resetPassword: (payload) => api.post('/auth/reset-password', payload).then((r) => r.data),
  me: () => api.get('/auth/me').then((r) => r.data),
  updateProfile: (payload) => api.put('/account/profile', payload).then((r) => r.data),
  changePassword: (currentPassword, newPassword) =>
    api.post('/account/change-password', { currentPassword, newPassword }).then((r) => r.data),
}

export default authService
