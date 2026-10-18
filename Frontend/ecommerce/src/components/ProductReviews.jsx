import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import productService from '../services/productService'
import accountService from '../services/accountService'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { ConfirmDialog } from './Modal'
import { Stars, StarInput } from './Rating'
import { Alert } from './ui'
import { getErrorMessage, getFieldErrors } from '../utils/errors'
import { formatDate } from '../utils/format'

const EMPTY_FORM = { rating: 0, title: '', comment: '' }

/** Rating summary, review list and — for customers who bought the product — a review form. */
export default function ProductReviews({ productId, onChanged }) {
  const { user } = useAuth()
  const toast = useToast()
  const location = useLocation()
  const isCustomer = user?.role === 'CUSTOMER'

  const [summary, setSummary] = useState(null)
  const [reviews, setReviews] = useState([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [eligibility, setEligibility] = useState(null)
  const [formOpen, setFormOpen] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const loadFirstPage = useCallback(async () => {
    const [s, r] = await Promise.all([productService.getReviewSummary(productId), productService.getReviews(productId, 0)])
    setSummary(s)
    setReviews(r.content)
    setPage(0)
    setHasMore(!r.last)
  }, [productId])

  const loadEligibility = useCallback(async () => {
    if (!isCustomer) return
    try {
      setEligibility(await accountService.getReviewEligibility(productId))
    } catch {
      setEligibility(null)
    }
  }, [isCustomer, productId])

  useEffect(() => {
    loadFirstPage().catch(() => setSummary({ average: 0, count: 0, distribution: {} }))
    loadEligibility()
  }, [loadFirstPage, loadEligibility])

  const loadMore = async () => {
    const next = await productService.getReviews(productId, page + 1)
    setReviews((list) => [...list, ...next.content])
    setPage(page + 1)
    setHasMore(!next.last)
  }

  const openForm = () => {
    const existing = eligibility?.existingReview
    setForm(existing ? { rating: existing.rating, title: existing.title || '', comment: existing.comment || '' } : EMPTY_FORM)
    setErrors({})
    setFormOpen(true)
  }

  const submit = async (event) => {
    event.preventDefault()
    if (!form.rating) {
      setErrors({ rating: 'Choose a star rating' })
      return
    }
    setSaving(true)
    try {
      await accountService.saveReview(productId, {
        rating: form.rating,
        title: form.title.trim() || null,
        comment: form.comment.trim() || null,
      })
      toast.success('Thank you — your review is published.')
      setFormOpen(false)
      await Promise.all([loadFirstPage(), loadEligibility()])
      onChanged?.()
    } catch (err) {
      setErrors(getFieldErrors(err))
      toast.error(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const remove = async () => {
    setSaving(true)
    try {
      await accountService.deleteReview(productId)
      toast.info('Your review was removed.')
      setConfirmDelete(false)
      setFormOpen(false)
      await Promise.all([loadFirstPage(), loadEligibility()])
      onChanged?.()
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  if (!summary) return null
  const existing = eligibility?.existingReview

  return (
    <section className="ss-section" id="reviews">
      <div className="ss-section-head">
        <div>
          <span className="ss-eyebrow">Customer reviews</span>
          <h2>What buyers say</h2>
        </div>
      </div>

      <div className="row g-4">
        <div className="col-lg-4">
          <div className="ss-card ss-card-pad">
            {summary.count > 0 ? (
              <>
                <div className="d-flex align-items-end gap-2">
                  <span className="ss-serif" style={{ fontSize: '3.2rem', lineHeight: 1 }}>
                    {Number(summary.average).toFixed(1)}
                  </span>
                  <span className="text-muted mb-1">out of 5</span>
                </div>
                <Stars value={summary.average} showValue={false} />
                <div className="small text-muted mt-1">
                  Based on {summary.count} verified {summary.count === 1 ? 'review' : 'reviews'}
                </div>
                <div className="mt-3 d-grid gap-2">
                  {[5, 4, 3, 2, 1].map((star) => {
                    const n = summary.distribution?.[star] || 0
                    const pct = summary.count ? Math.round((n / summary.count) * 100) : 0
                    return (
                      <div key={star} className="ss-rating-bar">
                        <span>{star} ★</span>
                        <div className="ss-rating-track" aria-hidden="true">
                          <div style={{ width: `${pct}%` }} />
                        </div>
                        <span className="text-muted">{n}</span>
                      </div>
                    )
                  })}
                </div>
              </>
            ) : (
              <p className="text-muted mb-0">No reviews yet. Customers who buy this product can share their experience here.</p>
            )}

            <div className="ss-divider" />
            {!user && (
              <p className="small mb-0">
                <Link to="/login" state={{ from: location }}>Sign in</Link> to review products you have bought.
              </p>
            )}
            {isCustomer && eligibility?.canReview && !formOpen && (
              <button type="button" className="btn btn-ink w-100" onClick={openForm}>
                <i className={`bi ${existing ? 'bi-pencil' : 'bi-star'} me-2`} />
                {existing ? 'Edit your review' : 'Write a review'}
              </button>
            )}
            {isCustomer && eligibility && !eligibility.canReview && (
              <p className="small text-muted mb-0">
                <i className="bi bi-info-circle me-1" />
                {eligibility.reason}
              </p>
            )}
          </div>
        </div>

        <div className="col-lg-8">
          {formOpen && (
            <form className="ss-card ss-card-pad mb-4" onSubmit={submit} noValidate>
              <h3 className="h4">{existing ? 'Edit your review' : 'Write a review'}</h3>
              <StarInput value={form.rating} onChange={(rating) => setForm((f) => ({ ...f, rating }))} disabled={saving} />
              {errors.rating && <div className="invalid-feedback d-block">{errors.rating}</div>}
              <label htmlFor="review-title" className="form-label mt-3">
                Headline (optional)
              </label>
              <input
                id="review-title"
                className="form-control"
                maxLength={120}
                value={form.title}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              />
              <label htmlFor="review-comment" className="form-label mt-3">
                Your review (optional)
              </label>
              <textarea
                id="review-comment"
                className="form-control"
                rows={4}
                maxLength={2000}
                value={form.comment}
                onChange={(e) => setForm((f) => ({ ...f, comment: e.target.value }))}
              />
              <div className="form-text">{form.comment.length}/2000</div>
              <div className="d-flex gap-2 mt-3 flex-wrap">
                <button type="submit" className="btn btn-ink" disabled={saving}>
                  {saving && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                  Publish review
                </button>
                <button type="button" className="btn btn-ghost" onClick={() => setFormOpen(false)} disabled={saving}>
                  Cancel
                </button>
                {existing && (
                  <button type="button" className="btn btn-danger-soft ms-auto" onClick={() => setConfirmDelete(true)} disabled={saving}>
                    Delete review
                  </button>
                )}
              </div>
            </form>
          )}

          {reviews.length === 0 ? (
            !formOpen && <Alert variant="info">Be the first to review this product after your purchase.</Alert>
          ) : (
            <div className="ss-card px-4">
              {reviews.map((r) => (
                <article key={r.reviewId} className="ss-review">
                  <Stars value={r.rating} showValue={false} />
                  {r.title && <h3 className="h5 mt-2 mb-1">{r.title}</h3>}
                  {r.comment && <p className="mb-2" style={{ whiteSpace: 'pre-line' }}>{r.comment}</p>}
                  <div className="small text-muted">
                    {r.reviewerName}
                    {r.verifiedPurchase && (
                      <span className="ss-verified">
                        <i className="bi bi-patch-check-fill" aria-hidden="true" /> Verified purchase
                      </span>
                    )}
                    · {formatDate(r.createdAt)}
                  </div>
                </article>
              ))}
            </div>
          )}
          {hasMore && (
            <button type="button" className="btn btn-ghost mt-3" onClick={loadMore}>
              Show more reviews
            </button>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="Delete your review?"
        confirmLabel="Delete"
        danger
        busy={saving}
        onConfirm={remove}
        onCancel={() => setConfirmDelete(false)}
      >
        Your rating will be removed from this product&apos;s average.
      </ConfirmDialog>
    </section>
  )
}
