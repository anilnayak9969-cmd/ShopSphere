// Remembers the last few products this browser viewed (per device, not per account).
const KEY = 'shopsphere.recentlyViewed'
const MAX = 8

export function getRecentlyViewed() {
  try {
    const list = JSON.parse(localStorage.getItem(KEY))
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

/** Stores just enough to render a small card; prices are re-fetched when the product is opened. */
export function rememberProduct(product) {
  try {
    const entry = {
      productId: product.productId,
      productName: product.productName,
      imageUrl: product.imageUrl,
      sellingPrice: product.sellingPrice,
      brand: product.brand || product.categoryName,
    }
    const rest = getRecentlyViewed().filter((p) => p.productId !== product.productId)
    localStorage.setItem(KEY, JSON.stringify([entry, ...rest].slice(0, MAX)))
  } catch {
    /* storage unavailable — feature silently disabled */
  }
}
