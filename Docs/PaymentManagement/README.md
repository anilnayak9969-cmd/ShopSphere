# ShopSphere — Payment Management Module

Payment processing and transaction history for every order. Payments go through the **Razorpay** payment gateway (cards, UPI, net banking, wallets including PayPal) or Cash on Delivery; administrators see every transaction and refund cancelled or returned orders. Built for the ITVedant / Vibgyor Advisors integrated internship.

**Submission document:** [Anil_Nayak_ShopSphere - Payment Management Module Assignment Submission.pdf](Anil_Nayak_ShopSphere%20-%20Payment%20Management%20Module%20Assignment%20Submission.pdf). Its source is [Payment_Management_Module.html](Payment_Management_Module.html), and the screenshots are in [screenshots/](screenshots/).

---

## Requirement checklist

| # | Requirement | Where it is implemented | Status |
|---|-------------|-------------------------|--------|
| 1 | **Process payment** through an external gateway (e.g. Stripe, PayPal); credit/debit cards, PayPal, bank transfers | **Razorpay** (Indian equivalent of Stripe): cards, UPI, net banking (bank transfer), wallets incl. PayPal, plus Cash on Delivery. `POST /api/customer/checkout` creates the Razorpay order; `POST /api/customer/payments/verify` checks the HMAC-SHA256 signature; `POST /api/payments/webhook` handles `payment.captured` / `payment.failed`. The method actually used is now saved (`payments.instrument`, e.g. CARD "Visa ···· 1111") | ✅ Done (method recording **added**) |
| 2 | **Payment dashboard**: transaction history with payment ID, amount, payment method and status | **Admin → Payments & refunds** (`AdminPayments.jsx`): summary cards, payment ID + Razorpay ID, order & customer, method, amount, refunded amount, status; status/method filters and search | ✅ Done (cards, method column, filters, search **added**) |
| 3 | **Refund payment** for cancelled or returned orders; refunds logged and displayed on the dashboard | Automatic refunds on cancellation / approved return, plus an admin **Refund ₹…** button (cancelled/returned orders only, never more than paid). Every refund is logged in the new `refunds` table and listed under **Refund history** | ✅ Done (admin refund, refund log **added**) |
| — | **Database design**: table `payments` | All columns exist; `instrument`, `instrument_detail` and the `refunds` table added | ✅ Done |

---

## How to use it

1. Start MySQL, the backend (`Backend/ecommerce`: `mvnw.cmd spring-boot:run`) and the frontend (`Frontend/ecommerce`: `npm run dev`). Put your Razorpay **test** keys in `application-secrets.properties` (`razorpay.key.id`, `razorpay.key.secret`; optional `razorpay.webhook.secret`).
2. **Customer:** add products to the cart → **Proceed to checkout** → **Pay online** (Razorpay window; test card `4111 1111 1111 1111`, any future expiry, any CVV) or **Cash on Delivery**.
3. **Admin:** sign in with the **Admin** tab → **Payments & refunds**.

| Task | Steps |
|------|-------|
| See all transactions | **Transactions** tab; filter All / Paid / COD due / Refunded / Failed / Any refund / Abandoned; **All methods** list; search by order number, customer or `pay_…` ID |
| Refund a payment | **Refund ₹…** on a cancelled or returned order → amount + reason → **Issue refund** |
| See refunds | **Refund history** tab: refund ID, payment/order, amount, how (Razorpay or manual transfer, with `rfnd_…` ID), reason, issued by, Processed/Failed |
| Customer view | **My account → Payments** |

### Refund rules

| Rule | Message |
|------|---------|
| Only for orders (or items) that are cancelled or returned | *Refunds can be issued only for cancelled or returned orders.* |
| Never more than was paid minus earlier refunds | *You can refund at most ₹….* |
| Payment must have been collected | *This payment has no collected amount left to refund.* |
| Amount ≥ ₹1, 2 decimals; reason 5–255 characters | Field errors in the form |
| Razorpay refund fails | Logged as **Failed**; *Razorpay could not process the refund …* |
| Online payments | Refunded through Razorpay to the original card / UPI / bank / wallet |
| Cash on delivery | Recorded as a **manual** refund for the finance team (bank or UPI transfer) |

---

## Database design

Table `payments`:

| Assignment column | Assignment type | ShopSphere column | Notes |
|-------------------|-----------------|-------------------|-------|
| `payment_id` | int (pk, auto_increment) | `payment_id` BIGINT PK AUTO_INCREMENT | |
| `order_id` | int (fk) | `order_id` BIGINT FK → `orders` | |
| `amount` | decimal(10,2) | `amount` DECIMAL(12,2) + `currency` | |
| `payment_method` | varchar(50) | `method` (RAZORPAY \| COD) + `instrument` (CARD, UPI, NETBANKING, WALLET, PAYPAL, EMI, PAY_LATER, CASH, OTHER) + `instrument_detail` | Detail is masked, e.g. "Visa credit ···· 1111", "pr···@okhdfc" |
| `payment_status` | varchar(50) | `status`: SUCCESS (paid), FAILED, REFUNDED, PENDING (cash due), CREATED (not completed), CANCELLED | |
| `created_at` | datetime | `created_at` DATETIME(6) | |
| `updated_at` | datetime | `updated_at` DATETIME(6) | |

Also: `razorpay_order_id`, `razorpay_payment_id`, `refunded_amount`, `failure_reason`. Card numbers, CVVs and UPI PINs are never stored.

New table `refunds`: `refund_id`, `payment_id` (FK), `amount`, `mode` (RAZORPAY \| MANUAL), `status` (PROCESSED \| FAILED), `razorpay_refund_id`, `reason`, `initiated_by`, `created_at`, `updated_at`.

On start, `SchemaPatcher` marks cash-on-delivery payments as `CASH` and logs refunds made before the table existed; `PaymentInstrumentBackfill` asks Razorpay for the method of earlier online payments. SQL for every action is in [`DataBase/queries.sql`](../../DataBase/queries.sql), section 10 (tested inside a rolled-back transaction).

---

## API

| Method | Path | Who | Purpose |
|--------|------|-----|---------|
| POST | `/api/customer/checkout` | Customer | Server-priced order + Razorpay order (or COD order) |
| POST | `/api/customer/payments/verify` | Customer | Signature check, record method, place order (idempotent) |
| POST | `/api/customer/payments/failed` | Customer | Record a failed / closed payment |
| GET | `/api/customer/payments` | Customer | Own payment history |
| POST | `/api/payments/webhook` | Razorpay | `payment.captured` / `payment.failed` (signed) |
| GET | `/api/admin/payments?status=&refundedOnly=&method=&search=&page=&size=` | Admin | Payment dashboard |
| GET | `/api/admin/payments/summary` | Admin | Collected (online/cash), refunded, net, counts, cash due |
| POST | `/api/admin/payments/{id}/refund` | Admin | `{ "amount": 49.00, "reason": "…" }` |
| GET | `/api/admin/refunds` · `/api/admin/payments/{id}/refunds` | Admin | Refund log |

Example: issue a refund with `curl` (replace `$TOKEN` and the payment ID):

```bash
curl -X POST http://localhost:8080/api/admin/payments/20/refund \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount":49.00,"reason":"Delivery fee refunded for the returned order"}'
```

---

## Code map

| Layer | Files |
|-------|-------|
| Gateway | `services/RazorpayGateway.java` (create order, refund → `rfnd_…` id, fetch payment method, signature checks) |
| Checkout | `services/impl/CheckoutServiceImpl.java` (create, verify, webhook, record method), `controller/CustomerController.java`, `controller/PaymentWebhookController.java` |
| Refunds | `services/RefundService.java` (gateway or manual refund + refund log), `entites/Refund.java`, `repositories/RefundRepository.java` |
| Admin | `controller/AdminController.java` (`GET /admin/payments`), `controller/AdminPaymentController.java`, `services/PaymentAdminService.java`, `services/impl/PaymentAdminServiceImpl.java` |
| Model / DTOs | `entites/Payment.java` (`instrument`, `instrumentDetail`), `enums/PaymentInstrument.java`, `dtos/order/PaymentResponse.java`, `PaymentSummaryResponse.java`, `RefundRequest.java`, `RefundResponse.java` |
| Data fixes | `config/SchemaPatcher.java`, `config/PaymentInstrumentBackfill.java` |
| Frontend | `src/pages/admin/AdminPayments.jsx`, `src/pages/Checkout.jsx`, `src/utils/razorpay.js`, `src/pages/customer/Payments.jsx`, `src/services/adminService.js` |
| Tests | `PaymentManagementTest` (7 tests), `CheckoutServiceImplTest` |

Run the module tests:

```bash
cd Backend/ecommerce
mvnw.cmd test -Dtest=PaymentManagementTest,CheckoutServiceImplTest
```
