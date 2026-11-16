# ShopSphere — Order Management Module

Processing and tracking of customer orders: customers place orders from their cart, and administrators view, filter and manage every order, update its status and cancel it before it ships. Built for the ITVedant / Vibgyor Advisors integrated internship.

**Submission document:** [Anil_Nayak_ShopSphere - Order Management Module Assignment Submission.pdf](Anil_Nayak_ShopSphere%20-%20Order%20Management%20Module%20Assignment%20Submission.pdf). Its source is [Order_Management_Module.html](Order_Management_Module.html), and the screenshots are in [screenshots/](screenshots/).

---

## Requirement checklist

| # | Requirement | Where it is implemented | Status |
|---|-------------|-------------------------|--------|
| 1 | **Place an order.** Customers add products to the cart; the total includes taxes and shipping | **Cart → Proceed to checkout** (Address → Review → Payment → Confirmation), `POST /api/customer/checkout`. The server prices the cart: total = items − coupon + shipping (₹49 below ₹499). Prices include GST; the GST share (18%) is stored in `orders.tax_amount` and shown as "Includes GST (18%)" | ✅ Done (GST line **added**) |
| 2 | **Order dashboard.** All orders, filters Pending / Shipped / Delivered / Cancelled; shows order ID, customer name, shipping address and status | **Admin → Orders** (`AdminOrders.jsx`): summary cards, **Pending / Shipped / Delivered / Cancelled / Returns** tabs, **Shipping address** column, search by order ID, customer name, email or city, plus pagination. `GET /api/admin/orders?group=…`, `GET /api/admin/orders/summary` | ✅ Done (tabs, cards, address column **added**) |
| 3 | **Update order status**, e.g. Pending → Shipped | **View / update → Move to → Update status**, `PATCH /api/admin/orders/{id}/status`. The admin can skip steps (Pending → Shipped); the steps in between are recorded in the tracking history, and the customer gets one notification | ✅ Done (status jumps **added**) |
| 4 | **Cancel order.** Customers and admins, only if not shipped yet; soft delete via status "Cancelled" | Customer: **Cancel order** on the order page (`POST /api/customer/orders/{id}/cancel`), now allowed until the order ships. Admin: **Cancel** with a warning dialog (`status = CANCELLED`). The row is kept: `status = CANCELLED`, `active = 0`, stock is returned and online payments are refunded | ✅ Done (admin Cancel button, cancel-until-shipped **added**) |
| — | **Database design:** table `orders` | All columns exist; the Boolean `status` column was **added** as `orders.active` (plus `tax_amount`) | ✅ Done |

---

## How to use it

1. Start MySQL, the backend (`Backend/ecommerce`: `mvnw.cmd spring-boot:run`) and the frontend (`Frontend/ecommerce`: `npm run dev`).
2. **Customer:** sign in at http://localhost:5173/login (on a local setup, **Try a demo account → Customer**), add products to the cart and check out.
3. **Admin:** sign in with the **Admin** tab and open **Orders** in the left menu.

| Task | Who | Steps |
|------|-----|-------|
| Place an order | Customer | Add to cart → **Cart** → **Proceed to checkout** → **Deliver here** → **Continue to payment** → choose **Pay online** or **Cash on Delivery** → **Place order** |
| See all orders | Admin | **Orders** → use the **Pending / Shipped / Delivered / Cancelled** tabs, the search box or **Any exact status** |
| Update status | Admin | **View / update** → **Move to** (e.g. Shipped) → optional note → **Update status** |
| Cancel (admin) | Admin | **Cancel** in the row (or **Cancel order** in the order window) → read the warning → **Cancel order** |
| Cancel (customer) | Customer | **My Orders** → the order → **Cancel order** → **Yes, cancel order** |

### Status groups

| Dashboard filter | Order statuses included |
|------------------|-------------------------|
| Pending | `PLACED`, `CONFIRMED`, `PROCESSING`, `PACKED`, `CANCELLATION_REQUESTED` (not shipped yet) |
| Shipped | `SHIPPED`, `OUT_FOR_DELIVERY` |
| Delivered | `DELIVERED` |
| Cancelled | `CANCELLED` (`active = 0`) |
| Returns | `RETURN_REQUESTED`, `RETURNED`, `REFUNDED` |

Unpaid online checkouts (`PENDING_PAYMENT`) are not counted as orders; `group=UNPAID` lists them.

### Rules

| Situation | Result |
|-----------|--------|
| Customer cancels before anything ships | Allowed: stock returned, Razorpay payment refunded, sellers notified |
| Customer cancels after shipping | Refused: *"This order has already been shipped, so it can no longer be cancelled. You can return it after delivery."* |
| Admin cancels an order whose items have all shipped | Refused: *"No item of this order can be cancelled any more."* |
| Admin moves an order backwards | Refused: *"No item of this order can move from its current status to …"* |
| Admin ships without a courier | ShopSphere Delivery is used, with tracking number `SSD-<order item id>` |
| Cash on Delivery order delivered | Payment marked paid automatically |
| Product sold out while in the cart | Checkout refused: *"… is no longer available in the requested quantity."* |

---

## Database design

Table `orders` (MySQL database `ecommerce_intern`):

| Assignment column | Assignment type | ShopSphere column | Notes |
|-------------------|-----------------|-------------------|-------|
| order id (written as `user_id`) | int (pk, auto_increment) | `order_id` BIGINT PK AUTO_INCREMENT | Plus a readable `order_number`, e.g. `SS261116-P8Q68X` |
| `customer_id` | int (fk) | `customer_id` BIGINT FK → `users` | |
| `total_amount` | decimal(10,2) | `total_amount` DECIMAL(12,2) | Also `subtotal`, `discount_amount`, `shipping_fee`, `tax_amount` |
| `order_status` | varchar(50) | `status` VARCHAR(30) | `PLACED` (pending) … `DELIVERED`, `CANCELLED` |
| `shipping_address` | varchar(300) | `shipping_name`, `shipping_phone`, `address_line1`, `address_line2`, `city`, `state`, `pincode` | A snapshot at checkout, joined into one line on the dashboard |
| `created_at` | datetime | `created_at` DATETIME(6) | |
| `updated_at` | datetime | `updated_at` DATETIME(6) | |
| `status` | boolean | `active` BIT(1) | 1 = active, 0 = cancelled. Set automatically from `status` before every save; `SchemaPatcher` fills in rows that existed before the column |

Related tables: `order_items` (one row per product, with its own `item_status`, courier and tracking number), `order_item_events` (tracking history) and `payments`.

SQL for every action (place, dashboard, filters, summary, update status, cancel) is in [`DataBase/queries.sql`](../../DataBase/queries.sql), section 8. It was tested against the local database inside a transaction that was rolled back.

---

## API

| Method | Path | Who | Purpose |
|--------|------|-----|---------|
| POST | `/api/customer/cart/items` | Customer | Add to cart `{ productId, quantity }` |
| POST | `/api/customer/checkout` | Customer | Place an order (address, optional `couponCode`, `paymentMethod`: `RAZORPAY` or `COD`) |
| POST | `/api/customer/payments/verify` | Customer | Confirm a Razorpay payment |
| GET | `/api/customer/orders`, `/api/customer/orders/{id}` | Customer | Own orders, one order with tracking |
| POST | `/api/customer/orders/{id}/cancel` | Customer | Cancel before shipping |
| GET | `/api/admin/orders?group=&status=&search=&page=&size=` | Admin | Order dashboard |
| GET | `/api/admin/orders/summary` | Admin | `{ total, pending, shipped, delivered, cancelled, returns }` |
| GET | `/api/admin/orders/{id}` | Admin | One order |
| PATCH | `/api/admin/orders/{id}/status` | Admin | `{ "status": "SHIPPED", "note": "…" }`; `"CANCELLED"` cancels |

Example: move an order to Shipped with `curl` (replace `$TOKEN` and the order ID):

```bash
curl -X PATCH http://localhost:8080/api/admin/orders/14/status \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"SHIPPED","note":"Packed and handed to ShopSphere Delivery."}'
```

---

## Code map

| Layer | Files |
|-------|-------|
| Controllers | `controller/CustomerController.java` (cart, checkout, cancel), `controller/AdminController.java` (orders section) |
| Services | `services/impl/CheckoutServiceImpl.java` (place order), `services/impl/OrderServiceImpl.java` (`adminOrders`, `adminOrderSummary`, `adminUpdateStatus`, `cancelByCustomer`), `services/PricingPolicy.java` (`shippingFor`, `includedGst`), `services/OrderSupport.java` (status from items, restock, refunds) |
| Entities / enums | `entites/CustomerOrder.java` (`active`, `taxAmount`, `getShippingAddress()`), `entites/OrderItem.java`, `enums/OrderStatus.java`, `enums/OrderStatusGroup.java` |
| DTOs | `dtos/order/OrderResponse.java` (`taxAmount`, `active`, `shippingAddress`), `dtos/order/OrderSummaryResponse.java`, `dtos/order/CheckoutRequest.java` |
| Repository | `repositories/OrderRepository.java` (`search` by status group, customer name, email, city) |
| Schema | `config/SchemaPatcher.java` (fills in `active` and `tax_amount` on existing rows) |
| Frontend | `src/pages/admin/AdminOrders.jsx` (dashboard), `src/pages/Cart.jsx`, `src/pages/Checkout.jsx` (GST line), `src/pages/customer/OrderDetails.jsx` (cancel, GST line), `src/services/adminService.js`, `src/utils/format.js` (`includedGst`) |
| Tests | `OrderManagementTest` (8 tests), `CheckoutServiceImplTest`, `OrderRulesTest` |

Run the module tests:

```bash
cd Backend/ecommerce
mvnw.cmd test -Dtest=OrderManagementTest,CheckoutServiceImplTest,OrderRulesTest
```
