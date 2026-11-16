# ShopSphere — Cart Management Module

Customers add, update and remove products in their shopping cart and review it before checkout, with prices, quantities and totals recalculated on every change. Administrators view customer carts and analyse cart abandonment. Built for the ITVedant / Vibgyor Advisors integrated internship.

**Submission document:** [Anil_Nayak_ShopSphere - Cart Management Module Assignment Submission.pdf](Anil_Nayak_ShopSphere%20-%20Cart%20Management%20Module%20Assignment%20Submission.pdf). Its source is [Cart_Management_Module.html](Cart_Management_Module.html), and the screenshots are in [screenshots/](screenshots/).

## Requirement checklist

| # | Requirement | Where it is implemented | Status |
|---|-------------|-------------------------|--------|
| 1 | **Add to cart** from the product page, stock validated | Product page **Add to cart / Buy now** → `POST /api/customer/cart/items`; refuses unavailable products, quantities above stock, and more than 10 units | ✅ Done |
| 2 | **Update cart**: quantity changes, total recalculated, stock checked | **− / +** on the cart page → `PUT /api/customer/cart/items/{id}`; live totals from current prices; stored `carts.total_price` / `cart_items.line_total` kept in step | ✅ Done (stored totals **added**) |
| 3 | **Remove from cart**, total recalculated | **Remove** / **Save for later** → `DELETE /api/customer/cart/items/{id}` | ✅ Done |
| 4 | **Cart dashboard**: customers see name, price, quantity, total; admins view carts and analyse abandonment | Customer **Shopping cart** page; new **Admin → Carts** page (`AdminCarts.jsx`): every customer cart, Active/Abandoned/Empty filter, abandonment rate, value left behind, most abandoned products, **View cart**, **Remind** | ✅ Done (admin page **added**) |
| — | **Database design**: table `carts` | Split into `carts` + `cart_items`; `total_price`, `total_quantity`, `line_total`, `added_at`, `updated_at`, `reminder_sent_at` **added** | ✅ Done |

## How to use it

- **Customer:** open a product → **Add to cart** → **Cart** (header) → change quantities with **− / +**, **Remove** or **Save for later** → **Proceed to checkout**.
- **Admin:** sign in → **Carts**. Filter **All / Active / Abandoned / Empty**, search by customer, choose the idle time (1 h – 3 days, default 1 day), **View cart**, **Remind** for abandoned carts.

**Definitions:** *Active* = items + activity within the idle time; *Abandoned* = items but idle longer; *Empty* = no items. **Abandonment rate** = abandoned carts ÷ (abandoned carts + orders placed in the last 30 days) × 100.

## Database design

| Assignment column | ShopSphere column |
|-------------------|-------------------|
| `cart_id` (pk) | `carts.cart_id` BIGINT PK |
| `customer_id` (fk) | `carts.user_id` BIGINT UNIQUE FK → `users` |
| `product_id` (fk) | `cart_items.product_id` FK → `products` (unique per cart) |
| `quantity` | `cart_items.quantity`; `carts.total_quantity` |
| `total_price` decimal(10,2) | `carts.total_price` DECIMAL(12,2); `cart_items.line_total` |
| `created_at` | `carts.created_at`; `cart_items.added_at` |
| `updated_at` | `carts.updated_at` (last activity, drives abandonment); `cart_items.updated_at` |

Also `cart_items.price_at_add` (price-change notice) and `carts.reminder_sent_at`. `SchemaPatcher` filled the new totals for existing carts. SQL for every action is in [`DataBase/queries.sql`](../../DataBase/queries.sql), section 11 (tested inside a rolled-back transaction).

## API

| Method | Path | Who | Purpose |
|--------|------|-----|---------|
| GET | `/api/customer/cart` | Customer | Cart with live prices and totals |
| POST | `/api/customer/cart/items` | Customer | Add `{ productId, quantity }` |
| PUT | `/api/customer/cart/items/{id}` | Customer | Update quantity `{ quantity }` |
| DELETE | `/api/customer/cart/items/{id}` | Customer | Remove |
| POST | `/api/customer/cart/items/{id}/save-for-later` | Customer | Move to wishlist |
| GET | `/api/admin/carts?state=&search=&abandonedAfterHours=&page=&size=` | Admin | Customer carts |
| GET | `/api/admin/carts/summary?abandonedAfterHours=` | Admin | Counts, values, abandonment rate, top abandoned products |
| GET | `/api/admin/carts/{id}` | Admin | One cart in full |
| POST | `/api/admin/carts/{id}/remind` | Admin | In-app reminder to the customer |

## Code map

| Layer | Files |
|-------|-------|
| Customer cart | `services/impl/CartServiceImpl.java` (stock checks, `refreshTotals`, live `toResponse`), `controller/CustomerController.java`, `entites/Cart.java`, `entites/CartItem.java`, `repositories/CartRepository.java` |
| Admin | `controller/AdminCartController.java`, `services/CartAdminService.java`, `services/impl/CartAdminServiceImpl.java`, `dtos/cart/AdminCartResponse.java`, `AdminCartDetailResponse.java`, `CartSummaryResponse.java` |
| Checkout link | `services/impl/CheckoutServiceImpl.java` removes bought items and refreshes the totals |
| Frontend | `src/pages/ProductDetails.jsx`, `src/pages/Cart.jsx`, `src/context/CartContext.jsx`, `src/pages/admin/AdminCarts.jsx`, `src/services/adminService.js` |
| Tests | `CartManagementTest` (12 tests) |

## Testing

**Unit tests** — `CartManagementTest` (12, all passing):

- adding to the cart checks stock and stores `total_price` / `total_quantity` / `line_total`
- adding above the stock, or an out-of-stock product, is refused
- adding the same product again increases its quantity
- updating the quantity recalculates the total; updating above the stock is refused and leaves the quantity unchanged
- removing an item recalculates the total
- another customer's cart item cannot be changed
- carts are classed as Active, Abandoned or Empty
- the summary works out the abandoned value, the abandonment rate (1 ÷ (1 + 7) = 12.5%) and the most abandoned products
- reminders are only for abandoned carts, and a cart is reminded only once

**End-to-end checks** against the running app (8 cart checks, all passing): adding above stock refused (409), add, update recalculates the total, more than 10 units refused (400), remove recalculates the total, admin summary, abandoned filter, customer refused from the admin API (403).

```bash
cd Backend/ecommerce
mvnw.cmd test -Dtest=CartManagementTest
```
