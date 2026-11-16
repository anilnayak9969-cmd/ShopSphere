# ShopSphere — Review and Rating Management Module

Customers who received a product rate it (1–5 stars) and review it; admins approve, reject or delete reviews; everyone sees the approved reviews and the average rating on the product page; customers update or delete their own reviews. Built for the ITVedant / Vibgyor Advisors integrated internship.

**Submission document:** [Anil_Nayak_ShopSphere - Review and Rating Management Module Assignment Submission.pdf](Anil_Nayak_ShopSphere%20-%20Review%20and%20Rating%20Management%20Module%20Assignment%20Submission.pdf) · source [Review_Rating_Management_Module.html](Review_Rating_Management_Module.html) · [screenshots/](screenshots/)

## What existed vs. what was added

Before this module, verified buyers could already rate and review products, see reviews on the product page, and edit or delete their own review; admins could delete reviews. Reviews went live immediately, and there was no `status` column. Moderation was added for this assignment.

| # | Requirement | Implementation | Status |
|---|-------------|----------------|--------|
| 1 | **Add a review / rating** for purchased products | **Write a review** on the product page: 1–5 stars, optional headline and text; only customers whose item was **delivered**, once per product → `PUT /api/customer/products/{id}/review`. New reviews wait for approval | ✅ Done (approval **added**) |
| 2 | **Review moderation** (admin) | **Admin → Reviews**: cards, pending first, status/star filters, search, **Approve**, **Reject** with a reason, **Delete** → `GET /api/admin/reviews`, `/summary`, `PATCH /{id}/approve`, `PATCH /{id}/reject`; customer and admins notified | ✅ **Added** |
| 3 | **View product reviews** | Product page: average, 5★–1★ bars, approved reviews with first name + *Verified purchase* → `GET /api/products/{id}/reviews`, `/summary` | ✅ Done (approved-only **added**) |
| 4 | **Delete / update reviews** | Customer: edit (checked again) or delete on the product page or **My reviews** (status + reason shown); admin: delete inappropriate reviews; rating recalculated each time | ✅ Done (status + re-check **added**) |
| — | **Database**: `reviews` | `reviews` with every assignment column; Boolean `status`, `moderation_status`, `rejection_reason`, `moderated_at` | ✅ Done (`status` + moderation columns **added**) |

## Moderation rules

- New and **edited** reviews are `PENDING` and hidden. An unchanged re-save keeps the current status.
- **Approve** → `APPROVED`, `status = 1`, shown on the product page and counted in `products.rating_average` / `rating_count`.
- **Reject** needs a reason (3–255 characters). The review becomes `REJECTED` with `status = 0`, and the customer sees the reason and can edit it and resubmit.
- **Delete** (admin) is permanent; the rating is recalculated and the customer is notified.
- Only the reviewer's first name is public; the admin queue shows their name and email.

## How to use it

- **Customer:** open a delivered product → **Write a review** → stars, headline, text → **Submit review**. See and manage reviews in **Account → My reviews**.
- **Admin:** **Admin console → Reviews** → **Approve**, **Reject** (choose a reason) or the bin to delete.

## Database

| Assignment | ShopSphere (`reviews`) |
|------------|------------------------|
| `review_id` | `review_id` BIGINT PK |
| `product_id` | `product_id` FK → `products` |
| `customer_id` | `user_id` FK → `users`; `(product_id, user_id)` unique |
| `rating` | `rating` INT (1–5) |
| `review_text` | `comment` VARCHAR(2000) + optional `title` VARCHAR(120) |
| `created_at` / `updated_at` | DATETIME(6) |
| `status` | `status` BIT(1): 1 approved, 0 unapproved |
| *(added)* | `moderation_status` (PENDING/APPROVED/REJECTED), `rejection_reason`, `moderated_at` |

On start-up, `SchemaPatcher` marks reviews written before moderation existed as approved and keeps `status` matching `moderation_status`. SQL for every action: [`DataBase/queries.sql`](../../DataBase/queries.sql), section 14. It is tested, and add/approve/reject/delete run in a rolled-back transaction.

## Code map

| Layer | Files |
|-------|-------|
| Backend | `services/impl/ReviewServiceImpl.java`, `services/ReviewService.java`, `repositories/ReviewRepository.java` (approved-only queries, moderation search, counts), `entites/Review.java` (`moderate(...)`), `enums/ReviewStatus.java`, `dtos/review/ReviewResponse.java` (status, admin-only customer details), `dtos/review/ReviewModerationDtos.java`, `controller/AdminController.java` (moderation endpoints), `config/SchemaPatcher.java` (`patchReviews`) |
| Frontend | `src/pages/admin/AdminReviews.jsx`, `src/components/ProductReviews.jsx` (approval status, submit for approval), `src/pages/customer/MyReviews.jsx` (status, reason, delete), `src/services/adminService.js`, `src/styles/features.css` |
| Tests | `ReviewManagementTest` (13), `ReviewServiceImplTest` (2) |

## Testing

**Unit tests** (15, all passing; full backend suite 138/138):

- a new review waits for approval and admins are notified; 1–5 star and 2000-character validation; only buyers whose item was delivered can review
- approve publishes the review and recalculates the rating; approving twice does nothing
- reject needs a reason and hides the review; moderation summary; queue filters
- the product page shows approved reviews only and never the email
- editing sends a review back for approval, and an unchanged save keeps it approved
- customer delete and admin delete, both with the rating recalculated and the customer notified

**End-to-end checks** against the running app (28, all passing):
- a product you didn't receive is refused; 0 and 6 stars are refused
- a pending review stays hidden; the queue and summary show it
- approve → product page and average update; edit → back to pending → re-approved
- reject with a reason → the customer sees it; notifications arrive
- customer delete and admin delete
- customers and sellers can't moderate (403); sellers can't review

```bash
cd Backend/ecommerce
mvnw.cmd test -Dtest=ReviewManagementTest,ReviewServiceImplTest
```
