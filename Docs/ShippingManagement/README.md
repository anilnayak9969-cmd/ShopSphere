# ShopSphere — Shipping Management Module

Shipping cost from weight, delivery location and shipping method; an admin shipping dashboard; customer tracking by tracking number; and admin updates of courier, tracking number and status. Built for the ITVedant / Vibgyor Advisors integrated internship.

**Submission document:** [Anil_Nayak_ShopSphere - Shipping Management Module Assignment Submission.pdf](Anil_Nayak_ShopSphere%20-%20Shipping%20Management%20Module%20Assignment%20Submission.pdf) · source [Shipping_Management_Module.html](Shipping_Management_Module.html) · [screenshots/](screenshots/)

## What existed vs. what was added

Before this module ShopSphere charged one flat delivery fee (₹49, free over ₹499) and stored a courier and tracking number per order item (filled in by sellers or delivery partners). The rest was added for this assignment.

| # | Requirement | Implementation | Status |
|---|-------------|----------------|--------|
| 1 | **Shipping cost calculation** by weight, location, method | `ShippingCalculator`: zone from city/state/PIN (metro / rest of India / remote), ₹15 per started 0.5 kg above 0.5 kg, **Standard** or **Express** chosen at checkout with price and arrival date → `POST /api/customer/shipping/quote`; checkout charges the chosen option | ✅ **Added** |
| 2 | **Shipping dashboard** (admin) | **Admin → Shipping**: status cards, search, status filter, table of order, destination + zone, method, cost, weight, courier, tracking, status, due date → `GET /api/admin/shipping`, `/summary` | ✅ **Added** |
| 3 | **Track shipment** (customer) | **Track my order** (`/track`): courier, status, progress, items, history; own orders only → `GET /api/customer/track/{number}` | ✅ **Added** |
| 4 | **Update shipping information** (admin) | **Ship / Update** modal: courier, tracking number, status (forward only), note → `PUT /api/admin/shipping/{orderId}`; moves the order's items, copies courier and tracking onto them, notifies the customer | ✅ **Added** |
| — | **Database**: `shipping` | Table `shipping` with every assignment column + `shipping_method`, `delivery_zone`, `weight_kg`, `estimated_delivery`, `shipped_at`, `delivered_at`; `products.weight_kg` | ✅ **Added** |

## Shipping cost rules

| Factor | Rule |
|--------|------|
| Zone | Metro city: base fee (₹49 from Admin → Settings) · Rest of India: +₹20 · Remote (North-East, J&K, Ladakh, islands): +₹50 |
| Weight | First 0.5 kg included, then ₹15 per started 0.5 kg |
| Standard | Free from ₹499 (still ₹15 per 0.5 kg above 2 kg) · 2–4 / 4–6 / 6–9 days |
| Express | 2 × (zone + weight charge), minimum ₹99, never free · 1–2 / 2–3 / 3–5 days |

Example: a 12.5 kg kettlebell to Nashik costs ₹315 Standard or ₹858 Express.

## How to use it

- **Customer:** Checkout → **Deliver here** → choose **Standard** or **Express** under *Delivery method*. Track with **Track my order** (footer), the **Track** link on an order, or the shipping notification.
- **Admin:** **Admin console → Shipping** → **Ship** / **Update** → courier, tracking number, status → **Save shipping details**.
- **Admin / seller:** set **Packed weight (kg)** on the product form.

## Database

| Assignment | ShopSphere (`shipping`) |
|------------|-------------------------|
| `shipping_id` | `shipping_id` BIGINT PK |
| `order_id` | `order_id` BIGINT FK → `orders`, UNIQUE |
| `courier_service` | `courier_service` VARCHAR(100) |
| `tracking_number` | `tracking_number` VARCHAR(100), indexed |
| `shipping_status` | `shipping_status` ENUM (PENDING, SHIPPED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURNED) |
| `shipping_cost` | `shipping_cost` DECIMAL(12,2) |
| `created_at` / `updated_at` | DATETIME(6) |
| *(added)* | `shipping_method`, `delivery_zone`, `weight_kg`, `estimated_delivery`, `shipped_at`, `delivered_at` |

On start-up `SchemaPatcher` gives every existing order a `shipping` row (courier, tracking and status from its items) and existing products a weight by category. SQL for every action: [`DataBase/queries.sql`](../../DataBase/queries.sql), section 13 (tested; the update runs in a rolled-back transaction).

## Code map

| Layer | Files |
|-------|-------|
| Backend | `services/ShippingCalculator.java`, `services/ShippingService.java`, `services/impl/ShippingServiceImpl.java`, `controller/ShippingController.java`, `entites/Shipment.java`, `repositories/ShipmentRepository.java`, `dtos/shipping/ShippingDtos.java`, `enums/ShippingMethod.java`, `enums/DeliveryZone.java`, `enums/ShippingStatus.java`; changed `CheckoutServiceImpl` (creates the shipment), `CartServiceImpl` / `CouponServiceImpl` (weight-based estimate), `OrderSupport.syncShipment`, `OrderResponse` (shipment info), `Product.weightKg`, `config/SchemaPatcher.java` (back-fill) |
| Frontend | `src/pages/admin/AdminShipping.jsx`, `src/pages/TrackShipment.jsx`, `src/pages/Checkout.jsx` (delivery method), `src/pages/customer/OrderDetails.jsx` (shipment), `src/services/shippingService.js`, `adminService.js`, product forms (weight), `AdminLayout.jsx` menu, `App.jsx` routes, `Footer.jsx` |
| Tests | `ShippingCalculatorTest` (7), `ShippingManagementTest` (11) |

## Testing

**Unit tests** (18, all passing; full backend suite 125/125):

- zones from city / state / PIN; zone prices ₹49 / ₹69 / ₹99; weight steps; free standard up to 2 kg and the heavy-parcel charge; express ×2 with ₹99 minimum; delivery dates; base fee from settings
- quote for both methods; empty cart refused
- shipping a pending order moves its items and uses the admin's courier and tracking number (no automatic SSD- number)
- in transit recorded; no backwards moves; tracking number of another order (409); cancelled orders refused; shipment status follows the order
- tracking by number; a step shared by the whole parcel shown once in the history; another customer's number (404)

**End-to-end checks** against the running app (25, all passing): metro / rest of India / remote quotes, invalid PIN, express order charged the quoted price, shipping row created, dashboard + summary, customer refused (403), invalid and duplicate tracking numbers, ship → in transit → delivered (cash on delivery marked paid), no backwards move, seller refused (403), tracking by number with history, unknown number (404), customer notified.

```bash
cd Backend/ecommerce
mvnw.cmd test -Dtest=ShippingCalculatorTest,ShippingManagementTest
```
