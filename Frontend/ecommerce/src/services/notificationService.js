import api from './api'

const notificationService = {
  list: (page = 0, size = 10) => api.get('/notifications', { params: { page, size } }).then((r) => r.data),
  unreadCount: () => api.get('/notifications/unread-count').then((r) => r.data),
  markRead: (id) => api.patch(`/notifications/${id}/read`),
  markAllRead: () => api.post('/notifications/read-all'),
}

export default notificationService
