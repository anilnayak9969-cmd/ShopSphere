import api from './api'

export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024
export const ACCEPTED_IMAGE_TYPES = 'image/jpeg,image/png,image/webp'

const uploadService = {
  /** Uploads one image (sellers and admins). Resolves to { url: '/api/images/<id>.jpg', ... }. */
  uploadImage: (file) => {
    const form = new FormData()
    form.append('file', file)
    return api
      .post('/uploads/images', form, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 })
      .then((r) => r.data)
  },
}

export default uploadService
