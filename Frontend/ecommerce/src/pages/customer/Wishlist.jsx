import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCart } from '../../context/CartContext'
import { useToast } from '../../context/ToastContext'
import { useWishlist } from '../../context/WishlistContext'
import { EmptyState, PageLoader, PriceTag, SafeImage } from '../../components/ui'
import { getErrorMessage } from '../../utils/errors'
import { formatDate } from '../../utils/format'

export default function Wishlist() {
  const { items, remove, moveToCart: moveItem } = useWishlist()
  const { refresh: refreshCart } = useCart()
  const toast = useToast()
  const [busy, setBusy] = useState(null)

  if (items === null) return <PageLoader label="Loading your wishlist…" />

  const moveToCart = async (product) => {
    setBusy(product.productId)
    try {
      await moveItem(product.productId)
      await refreshCart?.()
      toast.success(`${product.productName} moved to your cart`)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(null)
    }
  }

  const removeItem = async (product) => {
    setBusy(product.productId)
    try {
      await remove(product.productId)
      toast.info(`${product.productName} removed from your wishlist`)
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <span className="ss-eyebrow">Saved for later</span>
      <h1 className="mt-1 mb-4" style={{ fontSize: 'clamp(2.2rem, 4vw, 3rem)' }}>
        Wishlist
      </h1>
      {items.length === 0 ? (
        <div className="ss-card">
          <EmptyState
            icon="bi-heart"
            title="Nothing saved yet"
            action={
              <Link to="/products" className="btn btn-ink">
                Browse products
              </Link>
            }
          >
            Tap the heart on any product to keep it here.
          </EmptyState>
        </div>
      ) : (
        <div className="row g-3">
          {items.map(({ wishlistItemId, product, available, availability, availabilityLabel, addedAt }) => (
            <div key={wishlistItemId} className="col-sm-6 col-xl-4">
              <article className="ss-product-card">
                <Link to={`/products/${product.productId}`} className="ss-product-media">
                  <SafeImage src={product.imageUrl} alt={product.productName} />
                  {!available && <span className="ss-product-soldout">Unavailable</span>}
                </Link>
                <div className="ss-product-body">
                  <span className="ss-product-brand">{product.brand || product.categoryName}</span>
                  <Link to={`/products/${product.productId}`} className="ss-product-name">
                    {product.productName}
                  </Link>
                  <PriceTag price={product.price} sellingPrice={product.sellingPrice} discountPercent={product.discountPercent} />
                  <div className="d-flex justify-content-between align-items-center small mt-1">
                    <span className={`ss-pill ${availability === 'IN_STOCK' ? 'ss-pill-success' : availability === 'LOW_STOCK' ? 'ss-pill-warning' : 'ss-pill-muted'}`}>
                      {availabilityLabel}
                    </span>
                    <span className="text-muted">Saved {formatDate(addedAt)}</span>
                  </div>
                  <div className="d-flex gap-2 mt-2">
                    <button
                      type="button"
                      className="btn btn-ink btn-sm flex-grow-1"
                      disabled={!available || !product.inStock || busy === product.productId}
                      onClick={() => moveToCart(product)}
                    >
                      Move to cart
                    </button>
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      onClick={() => removeItem(product)}
                      disabled={busy === product.productId}
                      aria-label={`Remove ${product.productName}`}
                    >
                      <i className="bi bi-trash" />
                    </button>
                  </div>
                </div>
              </article>
            </div>
          ))}
        </div>
      )}
    </>
  )
}
