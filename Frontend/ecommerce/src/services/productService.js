import api, { cleanParams } from './api'

const productService = {
  /** params: search, categoryId, brand[], minPrice, maxPrice, inStock, minRating, minDiscount, sort, page, size */
  getProducts: (params = {}) => api.get('/products', { params: cleanParams(params) }).then((r) => r.data),
  getProduct: (id) => api.get(`/products/${id}`).then((r) => r.data),
  getSuggestions: (q) => api.get('/products/suggestions', { params: { q } }).then((r) => r.data),
  getBrands: (categoryId) => api.get('/products/brands', { params: cleanParams({ categoryId }) }).then((r) => r.data),
  getReviews: (id, page = 0, size = 5) => api.get(`/products/${id}/reviews`, { params: { page, size } }).then((r) => r.data),
  getReviewSummary: (id) => api.get(`/products/${id}/reviews/summary`).then((r) => r.data),
}

export default productService
