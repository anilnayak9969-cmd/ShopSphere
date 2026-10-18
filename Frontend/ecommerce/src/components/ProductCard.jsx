import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useToast } from '../context/ToastContext'
import { getErrorMessage } from '../utils/errors'
import { PriceTag, SafeImage, StockNote } from './ui'
import { Stars } from './Rating'
import WishlistButton from './WishlistButton'

export default function ProductCard({ product }) {
  const { user } = useAuth()
  const { addItem } = useCart()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const [adding, setAdding] = useState(false)

  // Sellers and admins browse the store but only customers have a cart.
  const canBuy = !user || user.role === 'CUSTOMER'
  const detailsUrl = `/products/${product.productId}`

  const handleAdd = async () => {
    if (!user) {
      navigate('/login', { state: { from: location } })
      return
    }
    setAdding(true)
    try {
      await addItem(product.productId, 1)
      toast.success(`${product.productName} added to your cart`)
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setAdding(false)
    }
  }

  return (
    <article className="ss-product-card">
      <Link to={detailsUrl} className="ss-product-media" aria-label={product.productName}>
        <SafeImage width={600} src={product.imageUrl} alt={product.productName} />
        {product.discountPercent > 0 && product.inStock && (
          <span className="ss-product-tag">{product.discountPercent}% off</span>
        )}
        {!product.inStock && <span className="ss-product-soldout">Sold out</span>}
      </Link>
      <WishlistButton product={product} />
      <div className="ss-product-body">
        <span className="ss-product-brand">{product.brand || product.categoryName}</span>
        <Link to={detailsUrl} className="ss-product-name">
          {product.productName}
        </Link>
        {product.ratingCount > 0 && <Stars value={product.ratingAverage} count={product.ratingCount} />}
        <PriceTag price={product.price} sellingPrice={product.sellingPrice} discountPercent={product.discountPercent} />
        <StockNote stock={product.stockQuantity} />
        {canBuy && (
          <button
            type="button"
            className="btn btn-outline-ink btn-sm mt-2"
            onClick={handleAdd}
            disabled={!product.inStock || adding}
          >
            {adding ? (
              <span className="spinner-border spinner-border-sm" aria-label="Adding" />
            ) : (
              <>
                <i className="bi bi-bag-plus me-2" aria-hidden="true" />
                {product.inStock ? 'Add to cart' : 'Unavailable'}
              </>
            )}
          </button>
        )}
      </div>
    </article>
  )
}
