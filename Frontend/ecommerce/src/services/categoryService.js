import api from './api'

const categoryService = {
  /** Active categories (with parentCategoryId for subcategories and product counts). */
  getCategories: () => api.get('/categories').then((r) => r.data),
}

/** Builds [{...category, children: [...]}] from the flat list, top-level first. */
export function toCategoryTree(categories) {
  const byParent = new Map()
  categories.forEach((c) => {
    const key = c.parentCategoryId ?? 'root'
    if (!byParent.has(key)) byParent.set(key, [])
    byParent.get(key).push(c)
  })
  const ids = new Set(categories.map((c) => c.categoryId))
  // A subcategory whose parent is inactive is shown at the top level.
  const roots = categories.filter((c) => c.parentCategoryId == null || !ids.has(c.parentCategoryId))
  return roots.map((c) => ({ ...c, children: byParent.get(c.categoryId) || [] }))
}

export default categoryService
