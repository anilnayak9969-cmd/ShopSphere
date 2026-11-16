# ShopSphere — Coupon and Discount Management Module

Admins create percentage or fixed-amount discount codes with validity dates, usage limits and optional order-total, product and category limits. A dashboard shows active and expired coupons. Customers apply codes in the cart and at checkout, and admins deactivate or soft-delete coupons. Built for the ITVedant / Vibgyor Advisors integrated internship.

**Submission document:** [Anil_Nayak_ShopSphere - Coupon and Discount Management Module Assignment Submission.pdf](Anil_Nayak_ShopSphere%20-%20Coupon%20and%20Discount%20Management%20Module%20Assignment%20Submission.pdf) · source [Coupon_Discount_Management_Module.html](Coupon_Discount_Management_Module.html) · [screenshots/](screenshots/)

## What existed vs. what was added

Coupons already existed:
- percentage or fixed codes, with dates, a usage limit, a minimum order and a cap;
- applying a code in the cart and at checkout, once per customer;
- admin create, edit and pause.

This module adds product and category limits, the active/expired dashboard, soft delete, and per-line discount shares.

| # | Requirement | Implementation | Status |
|---|-------------|----------------|--------|
| 1 | **Create a coupon / discount code** | **New coupon**: percentage (1–90%, optional ₹ cap) or fixed amount, valid from / expires, usage limit, minimum order total, and optional **products** and **categories** (subcategories included); the discount is worked out only on matching items → `POST/PUT /api/admin/coupons` | ✅ Done (product/category limits **added**) |
| 2 | **Coupon dashboard** | **Admin → Coupons**: cards, every coupon with discount, applies-to, validity, uses, discount given and a computed status (Active / Scheduled / **Expired** / Inactive / Used up / Deleted), filters and search, create/edit/deactivate/delete → `GET /api/admin/coupons`, `/summary` | ✅ Done (statuses, filters, cards **added**) |
| 3 | **Apply coupon at checkout** | **Have a coupon?** in the cart and checkout. The server validates the code, then price details and the total update → `POST /api/customer/coupons/apply`, `POST /api/customer/checkout` (`couponCode`). Each order line stores its share of the discount, so refunds are exact | ✅ Done (eligible-item pricing + line shares **added**) |
| 4 | **Deactivate / delete coupon** | **Deactivate/Activate** = Boolean `status` (`active`) → `PATCH /{id}/status`. **Delete** = soft delete (`deleted_at`, switched off, history kept) → `DELETE /{id}`; **Restore** → `PATCH /{id}/restore` | ✅ Done (soft delete **added**) |
| — | **Database**: `coupons` | `coupons` with every assignment column + `deleted_at`; `coupon_products`, `coupon_categories`; `order_items.discount_share` | ✅ Done |

## Rules

- **Status** is worked out in this order: Deleted → Expired → Inactive → Scheduled → Used up → Active.
- **Discount:** a percentage of the eligible items, capped by the maximum. A fixed amount never exceeds the eligible amount.
- **Validation at apply and again at order time:**
  - the code exists, is active and not deleted;
  - today is inside its validity dates, and its usage limit isn't reached;
  - the customer hasn't used it before;
  - the order total meets the minimum;
  - at least one cart item matches its product/category limits.
- **When saving a coupon:**
  - codes are unique and 4–30 letters/numbers;
  - a percentage can be at most 90%;
  - a fixed amount can't exceed the minimum order;
  - the end date must be after the start date;
  - a code that has been used can't be renamed.
- **Delete and restore:** a deleted coupon cannot be edited or activated until it is restored, and it comes back inactive.
- **Discount given** on the dashboard excludes cancelled orders.

## How to use it

- **Admin:** **Admin console → Coupons** → **New coupon** / **Edit** / **Deactivate** / bin (delete) → filter **Deleted** → **Restore**.
- **Customer:** cart or checkout → **Have a coupon?** → type the code → **Apply**.

Demo coupons:

| Code | State |
|------|-------|
| WELCOME10 | Active |
| BOOKS20 | Active |
| FLAT150 | Active |
| HOME15 | Active |
| DIWALI25 | Expired |
| NEWYEAR15 | Scheduled |

## Database

| Assignment | ShopSphere (`coupons`) |
|------------|------------------------|
| `coupon_id` | `coupon_id` BIGINT PK |
| `coupon_code` | `code` VARCHAR(30) UNIQUE |
| `discount_type` | `discount_type` ENUM('PERCENT','FLAT') |
| `discount_value` | `discount_value` DECIMAL(12,2) |
| `valid_from` / `valid_to` | `valid_from` / `valid_until` DATETIME(6) |
| `usage_limit` | `usage_limit` INT (+ `used_count`) |
| `status` | `active` BIT(1) |
| `created_at` / `updated_at` | DATETIME(6) |
| *(extra)* | `description`, `min_order_amount`, `max_discount`, **`deleted_at`**; tables **`coupon_products`**, **`coupon_categories`**, `coupon_redemptions`; **`order_items.discount_share`** |

SQL for every action: [`DataBase/queries.sql`](../../DataBase/queries.sql), section 15. It includes applying BOOKS20 to the demo cart in SQL. It was tested, and the create, edit, deactivate, delete and restore statements run in a rolled-back transaction.

## Code map

| Layer | Files |
|-------|-------|
| Backend | `services/impl/CouponServiceImpl.java`, `services/CouponService.java` (`CouponLine`, `AppliedCoupon`, `CouponSummary`), `controller/AdminCouponController.java`, `entites/Coupon.java` (`statusAt`, product/category sets, `deletedAt`), `enums/CouponStatus.java`, `repositories/CouponRepository.java` (`adminSearch`), `repositories/CouponRedemptionRepository.java`, `dtos/coupon/*`, `services/impl/CheckoutServiceImpl.java` (`shareDiscount`), `services/OrderSupport.java` (`refundShare`), `entites/OrderItem.java` (`discountShare`), `controller/PublicController.java` |
| Frontend | `src/pages/admin/AdminCoupons.jsx` (dashboard, product picker, categories, delete/restore), `src/components/CouponBox.jsx` (applies-to line), `src/services/adminService.js` |
| Tests | `CouponManagementTest` (14), `CouponServiceImplTest` (5) |

## Testing

**Unit tests** (19, all passing; full backend suite 152/152):
- create percentage and fixed coupons with limits;
- invalid coupons refused (duplicate code, over 90%, fixed above minimum, unknown product, bad dates);
- all six statuses; dashboard filters, discount given and summary;
- category coupon including a subcategory; no eligible item refused; minimum order checked on the whole order;
- discount shared only among eligible lines and used for refunds; proportional split with the remainder;
- deactivate/activate; soft delete and restore; code locked once used;
- caps, case-insensitive codes, and expired/inactive/used-up/reused codes.

**End-to-end checks** against the running app (32, all passing):
- create the demo coupons; invalid ones are refused;
- summary and filters;
- apply category, fixed, expired, scheduled, not-applicable and unknown codes;
- a cash-on-delivery order with a category coupon: discount on the lamp only, total recalculated, use counted;
- one use per customer;
- deactivate/activate, soft delete, deleted coupons hidden but kept, editing a deleted coupon refused, restore;
- the storefront shows only live coupons.

The test orders were cancelled. Their three test coupons (E2EHOME…) are soft-deleted.

```bash
cd Backend/ecommerce
mvnw.cmd test -Dtest=CouponManagementTest,CouponServiceImplTest
```
