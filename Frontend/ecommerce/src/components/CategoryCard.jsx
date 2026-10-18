import { Link } from 'react-router-dom'
import { SafeImage } from './ui'

/** Round image + name, linking to the category's products (including its subcategories). */
export default function CategoryCard({ category, productCount }) {
  return (
    <Link to={`/products?categoryId=${category.categoryId}`} className="ss-cat-card">
      <div className="ss-cat-card-img">
        <SafeImage src={category.imageUrl} alt="" width={240} eager />
      </div>
      <strong>{category.categoryName}</strong>
      {productCount > 0 && <small>{productCount} {productCount === 1 ? 'product' : 'products'}</small>}
    </Link>
  )
}
