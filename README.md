# ShopSphere — Multi-Vendor E-commerce Platform

A full-stack marketplace where **customers** shop, **sellers** run their own stores after approval, **delivery partners** apply to deliver, and **admins** manage the platform. Built for the ITVedant / Vibgyor Advisors integrated internship.

| Layer    | Technology |
|----------|------------|
| Frontend | React 19 + Vite, React Router 7, Bootstrap 5, Bootstrap Icons, Axios |
| Backend  | Java 17, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA / Hibernate, Bean Validation |
| Database | MySQL 8+ (database `ecommerce_intern`) |
| Payments | Razorpay (test mode): signed checkout callback + signed webhook, partial refunds; Cash on Delivery |
| Email    | Spring Mail over Gmail SMTP: OTP verification, password reset, order confirmation |

---

## Features

### Module 1 — Category Management (assignment module)
Built exactly against the given `categories` table (`category_id`, `category_name`, `description`, `created_at`, `updated_at`, `status`):

1. **Create category** with a name and description, plus an optional image. Names are unique, case-insensitive.
2. **Category dashboard**: every category with its **product count** (total and active), plus search, an Active/Inactive filter and pagination.
3. **Update category**: edit the name, description or image.
4. **Delete / deactivate**: a **soft delete** through `status`. If active products remain, the admin is **warned to assign them to another category first** and can move them in one click. The backend enforces the same rule (HTTP 409).
5. **Subcategories**: a category can have a parent (e.g. *Electronics → Audio*). Cycles are rejected, and browsing a department also shows products from its subcategories. Category images can be uploaded.

### Accounts & security
- **Separate full-screen Login and Register pages**, with no store header, an image panel that changes with the chosen role and links between the two.
- **Register by role**. Every role starts with name, email, 10-digit mobile, a strong password and acceptance of the terms, then adds its own details:

  | Role | Extra details at sign-up | After email verification |
  |------|--------------------------|--------------------------|
  | Customer | none | Can shop immediately |
  | Seller | Store name, business type (individual, proprietorship, partnership/LLP, private limited), GSTIN (optional, format-checked), pickup address (address, city, state, PIN) | Application `PENDING` until an admin approves |
  | Delivery partner | Vehicle (bicycle, motorcycle, scooter, electric scooter), registration number (not needed for a bicycle; Indian format, including BH series), service city and PIN | Application `PENDING` until an admin approves |
  | Admin | Register → **Admin** card with the secret **admin access code** (`app.admin.registration-code`), or created by another admin from **Admin → Users → Add admin**. The first admin also comes from the secrets file | Signs in to the full admin console straight away. Every existing admin is notified. Wrong codes are rate-limited (5 tries, then a 15-minute lock). An empty code turns admin sign-up off |

  Identity documents (PAN, driving licence, bank details) are **not uploaded or stored**. They belong to in-person or KYC onboarding, which keeps sensitive data out of the database.
- **Login with role tabs** (Customer · Seller · Delivery partner · Admin). One form works for every role, because the server decides the role from the account. The tab only adjusts the wording. After sign-in, each role goes to its own home: the store, `/seller`, `/delivery` or `/admin`.
- Then confirm with a **6-digit email OTP**. The OTP expires after 10 minutes, allows 5 attempts, has a 60-second resend cooldown and is stored only as a BCrypt hash.
- Forgot/reset password by OTP; **change password** and **edit profile** while signed in.
- JWT login with BCrypt passwords and a 15-minute lockout after 5 failed attempts.
- Role-based access: `/api/admin/**` is ADMIN only, `/api/seller/**` SELLER only, `/api/delivery/**` DELIVERY_PARTNER only and `/api/customer/**` CUSTOMER only. The frontend routes are protected the same way.
- Seller approval (`PENDING → APPROVED / REJECTED / SUSPENDED`) is kept separate from the role.

### Shopping
- **Animated home page**: a staggered hero entrance, floating product collage and drifting colour orbs, a rotating headline word, live catalogue counters (products, brands, departments, all from the database), a trust strip that scrolls and pauses on hover, sections that fade in as you scroll, and image zoom on hover. Every animation is switched off for visitors who prefer reduced motion.
- Role-based navigation. Guests see Home, Products, Login, Register and Cart. Customers also get Wishlist, My Orders, Account and Logout. Sellers and admins get their own consoles, and a notification bell appears for every signed-in user.
- Home page: hero with search → departments → featured → deals → best sellers → new arrivals → benefits → promo banner → footer.
- Search with live suggestions. Filters for category, **brand**, price, **minimum rating**, **minimum discount** and in-stock. Sorts for newest, price, discount, **popularity** and top rated. Pagination. All of it runs as database queries, and the filters live in the URL so results can be shared.
- Product page with a **photo gallery**, a **specifications table**, stock status, a **Save to wishlist** button and related products.
- Cart with **save for later**, a coupon box and a **price-change notice** when a seller changes a price after you added the item.
- Footer pages: About, Contact (stored for the admin inbox), Help/FAQ, Shipping, Returns, Privacy, Terms, and seller registration.
- **Ratings & reviews**: only customers who bought the product can review, and each review is marked **Verified purchase**. The page shows a star summary and a 5★–1★ breakdown.
- **Wishlist**, a persistent **cart**, and an **address book** with a default address.
- **Help centre** page covering shipping, cancellations, payments and accounts.

### Checkout & payments (Razorpay + Cash on Delivery)
Checkout has four steps: **Address → Review → Payment → Confirmation**.

**Cash on Delivery** places the order immediately and reserves stock. Its payment stays `PENDING` and becomes `SUCCESS` when the order is delivered, or `CANCELLED` if the order is cancelled first. Admins can switch COD off or set a maximum order value.

**Razorpay**:
1. The customer picks a saved address or enters a new one, and optionally applies a **coupon**.
2. `POST /api/customer/checkout`: the **server** prices the cart, re-validates the coupon, snapshots the address and prices, and creates the Razorpay order.
3. Razorpay Checkout collects the payment.
4. `POST /api/customer/payments/verify`: the server checks the **HMAC-SHA256 signature**. It then locks the payment row and the product rows (`SELECT … FOR UPDATE`), deducts stock, records the coupon redemption, places the order and emails a confirmation.
5. `POST /api/payments/webhook`: Razorpay's server-to-server `payment.captured` / `payment.failed` events, verified against the **raw body** with a separate **webhook secret**. The order is completed even if the customer closes the browser.
   - Every path is safe to repeat: stock is never deducted twice, and a sold-out race refunds the payment automatically.

### Coupons
Admins create **percentage** coupons (with an optional cap) or **flat** coupons. Each can have a minimum order, start/end dates and a total usage limit. **Each customer can use a coupon once.** The demo coupon is **`WELCOME10`**: 10% off orders of ₹499 or more, capped at ₹500.

### Orders, fulfilment, returns
- Each **order line has its own status**, because one order can contain products from several sellers: `PLACED → CONFIRMED → PACKED → SHIPPED → OUT_FOR_DELIVERY → DELIVERED`. Shipping requires a **courier and tracking number**, which the customer sees on the order page. The order's overall status follows its least-advanced item.
- Cancelling restocks the items and refunds online payments through Razorpay. Partial refunds are supported, so cancelling one item of a multi-seller order refunds only that item.
- **Returns**: within the return window (Admin → Settings, 7 days by default), a customer can return a delivered item with a reason. The seller (or an admin) approves or rejects it. Approval restocks the item and refunds it: online payments go through Razorpay, and COD refunds are recorded for a manual transfer.
- **Notifications** for every step: order placed/confirmed/shipped/delivered, return decisions, product approvals, seller approvals and new contact messages.

### Customer account
Profile, Orders (with tracking and returns), **Returns & refunds**, **Payment history**, Wishlist, Addresses, **My reviews** and **Notifications**.

### Seller studio
- **Overview**: revenue, items to fulfil, delivered items, open returns, products awaiting approval and low stock. Every card links to its page.
- **Products**: an approval badge (and the admin's rejection reason) on each product. The add/edit form includes **image upload**, up to 5 gallery photos, a **specifications editor** and a live preview. New products, and edits to their content, need **admin approval**. Price and stock changes go live immediately.
- **Inventory** (low-stock and out-of-stock filters, inline stock editing), **Orders** (fulfilment actions and the ship dialog), **Returns**, **Sales reports** (daily chart, top products, status counts), **Commission & earnings** (monthly statement) and **Store profile** (logo, support contacts, GSTIN, business address).

### Admin console
Overview (with the seller-application queue and "needs attention" links), **Users** (suspend or reactivate any account), **Sellers** (approve, reject or suspend stores), **Categories** (parent category, image upload), **Products** (an approval queue with approve or reject-with-reason, plus take-downs), **Orders** (item statuses and tracking), **Payments & refunds**, **Returns**, **Coupons**, **Reviews**, **Reports** (orders per day, top categories and sellers, status breakdown, low stock, refunds, estimated commission), **Messages** and **Settings** (commission, shipping fee, free-shipping threshold, COD, return window, low-stock threshold).

### Starter catalogue and demo accounts
With `app.seed.demo-data=true`, the backend loads `src/main/resources/seed/catalog.json` on start. It is a realistic marketplace:

- **6 stores**, each with its own seller account and store profile: TechTrove Electronics (Bengaluru), Thread & Loom (Jaipur), UrbanNest Home (Pune), GlowBar Beauty (Mumbai), PageTurner Books (Kolkata) and ActivePeak Sports (New Delhi).
- **6 departments with 25 subcategories**, for example Electronics → Mobiles & Tablets, Laptops & Computers, Audio, Cameras, Wearables.
- **77 products** with full descriptions, 363 specification rows, prices and discounts in rupees, stock levels, and checked Unsplash photos (19 with extra gallery photos). Every photo was checked by eye, and photos showing real brand logos were left out. Brands are the stores' own labels.

Seeding is safe to repeat. Existing rows are never overwritten, so a seller's or admin's edits stay; old `DEMO-` products are upgraded in place. To load the same data without starting the app, run `DataBase/catalog_data.sql`.

**Demo accounts.** The sign-in page shows **Try a demo account** buttons that sign in with one click:

| Role | Email | Password |
|------|-------|----------|
| Customer (with a saved address) | `demo.customer@shopsphere.local` | `app.demo.password` (Demo@12345 locally) |
| Seller — TechTrove Electronics | `demo.seller@shopsphere.local` | same |
| Delivery partner (approved) | `demo.rider@shopsphere.local` | same |
| Admin | `app.admin.email` | `app.admin.password`; listed only when `app.demo.show-admin-login=true` |

The other five stores (`threadloom@`, `urbannest@`, `glowbar@`, `pageturner@`, `activepeak@shopsphere.local`) also use the demo password and appear as **Other demo stores** buttons. An order goes to the store that sells the product: a shirt order, for example, appears for Thread & Loom, not TechTrove. The buttons come from `GET /api/public/demo-accounts`, which returns nothing when demo data is off, so production never shows them.

### Delivery partners and the order journey
Every order line moves through one flow, with the customer notified at each step:

1. The **customer** places the order (Razorpay or cash on delivery).
2. The **seller** of that product confirms it and marks it **Packed**.
3. Packed parcels appear under **Available pickups** for approved **delivery partners**, with parcels in their service city listed first. Until a partner accepts, they see only the pickup store and the destination city and PIN, not the customer's name, phone or address.
4. A partner taps **Accept pickup**. This assigns the parcel atomically, so two partners can never take the same one. It becomes *Shipped* with courier "ShopSphere Delivery" and tracking number `SSD-<item id>`, and the full drop address and phone are revealed.
5. The partner marks it **Out for delivery**, then **Delivered**. Cash-on-delivery payments are marked paid on delivery, and the partner's dashboard shows today's and total deliveries.

**Sellers only confirm, pack or cancel.** Shipped, out for delivery and delivered are recorded only by the delivery partner who has the parcel, or by an admin for exceptions. The API enforces this too: a seller request for any other step is refused with `SELLER_STEP_NOT_ALLOWED`. Once a partner has the parcel, the seller sees the partner's name and phone. Admins review partner applications on **Admin → Delivery partners**.

**Live tracking.** Every signed-in tab keeps a Server-Sent Events stream open (`GET /api/live/stream`, opened with `fetch` so the JWT stays in the Authorization header). Each notification is pushed the moment the database change commits, typically within about 30–40 ms:
- The customer's order page refreshes itself, shows a green **Live tracking** badge, animates the newly reached step, and says who has the item now (for example, "With delivery partner Ravi — picked up from UrbanNest Home").
- A **Tracking history** lists every step with its time, from the new `order_item_events` table.
- Sellers see new orders appear, delivery partners see new pickups the moment a seller packs one, and the bell count and pop-up message update everywhere.
- If the stream drops, the browser reconnects with back-off, and the order page checks every 20 seconds in the meantime.

**Each browser tab keeps its own sign-in.** The token is held per tab (sessionStorage), and the latest sign-in is remembered for new tabs (localStorage). Signing in as a seller in one tab therefore no longer turns another tab's customer session into the seller, which used to cause "You do not have permission to access this resource" errors.

### Secure image uploads
`POST /api/uploads/images` (sellers and admins only) accepts JPG, PNG or WEBP up to 5 MB. The **file content is checked by its magic bytes**, so a script renamed to `.png` is rejected. Files are stored under random UUID names in `${user.home}/shopsphere-uploads` (set `app.upload.dir` to change this) and served from `/api/images/{name}` with path-traversal protection.

---

## Project structure

```
My_Project_2/
├── Backend/ecommerce/                  Spring Boot app
│   └── src/main/java/com/example/ecommerce/
│       ├── config/          SecurityConfig (CORS, role rules), DataInitializer (admin, settings, demo
│       │                    catalog), SchemaPatcher (small schema fixes Hibernate cannot do)
│       ├── controller/      Auth, Account, Category, Product, Public, UserFeatures (notifications,
│       │                    uploads), Customer, CustomerAccount, Seller, Admin, AdminCategory,
│       │                    AdminCoupon, PaymentWebhook
│       ├── dtos/            Request/response records with validation (auth, account, category,
│       │                    product, cart, order, returns, coupon, review, wishlist, admin, common)
│       ├── entites/         JPA entities (23 tables)
│       ├── enums/           Role, SellerStatus, OrderStatus (transitions), PaymentStatus, PaymentMethod,
│       │                    ApprovalStatus, ReturnStatus, DiscountType, OtpPurpose
│       ├── exception/       ApiException + GlobalExceptionHandler (one JSON error format)
│       ├── repositories/    Spring Data repositories + ProductSpecifications (dynamic filters)
│       ├── security/        UserPrincipal, login rate limiting, jwt/ (JwtUtils, AuthTokenFilter)
│       └── services/        Interfaces, RazorpayGateway, PricingPolicy, OrderSupport, RefundService,
│                            ReportService, FileStorageService, NotificationService, SettingsService;
│                            impl/ business logic
├── Frontend/ecommerce/                 React app (http://localhost:5173)
│   └── src/
│       ├── App.jsx          BrowserRouter + all routes
│       ├── App.css          Design system;  styles/features.css, styles/components.css
│       ├── components/      Navbar, Footer, SearchBar, ProductCard, ProductReviews, Rating, WishlistButton,
│       │                    NotificationBell, ImageUpload, BarChart, CheckoutSteps, CouponBox,
│       │                    ReturnsManager, AddressForm, Modal, Pagination, form controls, UI kit
│       ├── hooks/           useDebounce, usePagedList
│       ├── context/         Auth, Cart, Wishlist, Toast
│       ├── layouts/         MainLayout, AccountLayout, SellerLayout, AdminLayout
│       ├── pages/           Store pages, info/ (About, Contact, policies), auth/, customer/, seller/, admin/
│       │                    (seller and admin pages are lazy-loaded)
│       ├── routes/          ProtectedRoute / GuestRoute
│       ├── services/        Axios API calls
│       └── utils/           Formatting, validation, errors, Razorpay loader, recently viewed, coupon storage
└── DataBase/
    ├── Internship_Ecommerce.sql        Creates the database, all 23 tables, platform settings and the coupon
    ├── catalog_data.sql                Stores, category tree, 77 products, photos, specifications, demo accounts
    └── queries.sql                     Category Management SQL + reports, approval queue, fulfilment, returns, commission
```

---

## Running locally

### Prerequisites
- JDK 17 or newer, Node.js 20+, MySQL 8+ running on `localhost:3306`

### 1. Database
In MySQL Workbench open `DataBase/Internship_Ecommerce.sql` and execute it, or run:
```bash
mysql -u root -p < DataBase/Internship_Ecommerce.sql
```
This creates `ecommerce_intern`, all 23 tables, the platform settings row and the `WELCOME10` coupon. Then load the catalogue with `mysql -u root -p ecommerce_intern < DataBase/catalog_data.sql`, or just start the backend, which loads the same catalogue itself. It is safe to run more than once. The backend also creates and updates tables automatically, so this step is optional. `DataBase/queries.sql` contains ready-to-run queries for the Category Management module and for reports.

### 2. Backend secrets
Copy `Backend/ecommerce/src/main/resources/application-secrets.example.properties` to `application-secrets.properties` in the same folder and fill it in. This file is git-ignored.

| Property | What to put |
|----------|-------------|
| `spring.datasource.password` | Your MySQL root password |
| `spring.app.jwtSecret` | A random string of at least 64 characters |
| `razorpay.key.id` / `razorpay.key.secret` | Razorpay Dashboard → Settings → API Keys (**Test mode**) |
| `razorpay.webhook.secret` | Optional. Razorpay Dashboard → Webhooks (see below). Leave empty to disable the webhook |
| `spring.mail.username` / `spring.mail.password` | Gmail address + **Gmail App Password** (Google Account → Security → 2-Step Verification → App passwords) |
| `app.admin.email` / `app.admin.password` | Login for the first admin account (more admins are added from Admin → Users) |
| `app.admin.registration-code` | Secret code for admin sign-up on the Register page. Use a long random value; leave it empty to turn admin sign-up off |
| `app.demo.password` | Password for all demo accounts (customer, stores, delivery partner) |
| `app.demo.show-admin-login` | `true` only on your own machine, to list the admin as a demo button |

> **No Gmail yet?** Leave the mail username empty. OTP codes are then printed in the backend console (`[DEV MODE] … code for x@y.com: 123456`). Configure real SMTP before deploying.

### 3. Start the backend
```bash
cd Backend/ecommerce
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```
The API runs at `http://localhost:8080/api`.

### 4. Start the frontend
```bash
cd Frontend/ecommerce
npm install
npm run dev
```
Open **http://localhost:5173**. The port is fixed (`strictPort` in `vite.config.js`) because the backend only allows that origin. If you see `Port 5173 is already in use`, an old dev server is still running. Close that terminal, or end the leftover `node.exe` in Task Manager, then run `npm run dev` again.

### Accounts for testing
| Role | Login |
|------|-------|
| Admin | `app.admin.email` / `app.admin.password` from your secrets file |
| Demo seller | `demo.seller@shopsphere.local` / `app.demo.seller-password` |
| Demo accounts | One click on the sign-in page (see *Starter catalogue and demo accounts*) |
| New customer / seller / delivery partner | Register at `/register` (choose the role), then enter the OTP from your email (or the console). Approve sellers and partners as admin |

**Razorpay test card:** `4111 1111 1111 1111`, any future expiry, any CVV. **Demo coupon:** `WELCOME10`.

### Razorpay webhook (optional, needs a public URL)
In the Razorpay Dashboard (Test mode), add a webhook with URL `https://<your-backend>/api/payments/webhook`, events `payment.captured` and `payment.failed`, and a secret of your choice. Put the same secret in `razorpay.webhook.secret`. For local testing, expose port 8080 with a tunnel such as ngrok.

---

## API reference

All endpoints are prefixed with `/api`. Protected endpoints need the header `Authorization: Bearer <token>`. Errors share one format:
```json
{ "status": 409, "code": "CATEGORY_HAS_PRODUCTS", "message": "...", "fieldErrors": { "field": "message" } }
```

### Public
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/auth/register`, `/auth/verify-otp`, `/auth/resend-otp`, `/auth/login` | Sign-up by role (`CUSTOMER`, `SELLER`, `DELIVERY_PARTNER`) with email OTP, login (JWT) |
| POST | `/auth/forgot-password`, `/auth/reset-password` | Password reset by OTP |
| GET | `/categories` | Active categories (with `parentCategoryId`) and product counts |
| GET | `/products?search=&categoryId=&brand=&brand=&minPrice=&maxPrice=&minRating=&minDiscount=&inStock=&sort=&page=&size=` | Catalog (approved, active products only). `sort` is one of `newest`, `price_asc`, `price_desc`, `discount`, `popular`, `rating`, `name` |
| GET | `/products/suggestions?q=` · `/products/brands` · `/products/{id}` | Suggestions · brand list · details (gallery + specifications) |
| GET | `/products/{id}/reviews` · `/products/{id}/reviews/summary` | Reviews · average + star breakdown |
| GET | `/public/settings` · `/public/coupons` · `/public/demo-accounts` | Shipping/COD/return rules · currently valid coupons · demo logins (empty in production) |
| POST | `/contact` | Contact form |
| GET | `/images/{name}` | Uploaded images |
| POST | `/payments/webhook` | Razorpay webhook (signature-verified) |

### Signed-in user (any role)
`GET /live/stream` (Server-Sent Events: `connected`, `notification`, `pickups`) · `GET /auth/me`, `PUT /account/profile`, `POST /account/change-password`, `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `POST /notifications/read-all`. Sellers and admins also get `POST /uploads/images` (multipart field `file`).

### Customer
| Area | Endpoints |
|------|-----------|
| Cart | `GET /customer/cart`, `POST /customer/cart/items`, `PUT/DELETE /customer/cart/items/{id}`, `POST /customer/cart/items/{id}/save-for-later`, `POST /customer/cart/acknowledge-prices` |
| Wishlist | `GET /customer/wishlist`, `POST/DELETE /customer/wishlist/{productId}` |
| Addresses | `GET/POST /customer/addresses`, `PUT/DELETE /customer/addresses/{id}`, `PATCH /customer/addresses/{id}/default` |
| Coupons | `POST /customer/coupons/apply` (preview on the current cart) |
| Checkout | `POST /customer/checkout` (`paymentMethod`: `RAZORPAY` or `COD`), `POST /customer/payments/verify`, `POST /customer/payments/failed`, `GET /customer/payments` |
| Orders & returns | `GET /customer/orders`, `GET /customer/orders/{id}`, `POST /customer/orders/{id}/cancel`, `POST/GET /customer/returns` |
| Reviews | `GET/PUT/DELETE /customer/products/{productId}/review`, `GET /customer/reviews` |

### Delivery partner
`GET/PUT /delivery/profile`, `GET /delivery/dashboard`, `GET /delivery/pickups`, `GET /delivery/jobs?state=active|completed`, `POST /delivery/jobs/{orderItemId}/accept`, `PATCH /delivery/jobs/{orderItemId}/status` (`OUT_FOR_DELIVERY`, then `DELIVERED`)

### Seller
| Area | Endpoints |
|------|-----------|
| Dashboard & store | `GET /seller/dashboard`, `GET/PUT /seller/profile` |
| Products | `GET/POST /seller/products?search=&approval=&maxStock=`, `GET/PUT /seller/products/{id}`, `PATCH /seller/products/{id}/status`, `PATCH /seller/products/{id}/stock` |
| Fulfilment | `GET /seller/orders?status=`, `PATCH /seller/order-items/{id}/status` (`courierName` + `trackingNumber` required for `SHIPPED`) |
| Returns | `GET /seller/returns?status=`, `PATCH /seller/returns/{id}` |
| Reports | `GET /seller/reports/sales?days=`, `GET /seller/reports/commission` |

### Admin
| Area | Endpoints |
|------|-----------|
| Category Management | `GET/POST /admin/categories`, `GET/PUT/DELETE /admin/categories/{id}`, `PATCH /admin/categories/{id}/status`, `POST /admin/categories/{id}/reassign-products` |
| Dashboard & people | `GET /admin/dashboard`, `GET /admin/users?role=`, `POST /admin/admins` (create an admin), `PATCH /admin/sellers/{id}/status`, `PATCH /admin/users/{id}/status`, `GET /admin/delivery-partners?status=&search=`, `PATCH /admin/delivery-partners/{id}/status` |
| Products & orders | `GET /admin/products?approval=`, `PATCH /admin/products/{id}/status`, `PATCH /admin/products/{id}/approve`, `PATCH /admin/products/{id}/reject`, `GET /admin/orders`, `GET /admin/orders/{id}`, `PATCH /admin/orders/{id}/status` |
| Payments & returns | `GET /admin/payments?status=&refundedOnly=`, `GET /admin/returns?status=`, `PATCH /admin/returns/{id}` |
| Reports, settings, inbox | `GET /admin/reports?days=`, `GET/PUT /admin/settings`, `GET /admin/messages?resolved=`, `PATCH /admin/messages/{id}` |
| Coupons | `GET/POST /admin/coupons`, `PUT /admin/coupons/{id}`, `PATCH /admin/coupons/{id}/status` |
| Reviews | `GET /admin/reviews?rating=`, `DELETE /admin/reviews/{id}` |

---

## Database design (23 tables)

| Table | Purpose / key relationships |
|-------|------------------------------|
| `users` | All accounts: `role`, `seller_status` (application status for sellers and delivery partners), `email_verified`, `active` |
| `email_otps` | Hashed one-time codes with purpose, expiry and attempts |
| `seller_profiles` | One per seller: business type, GSTIN, pickup/business address, description, logo, support contacts |
| `delivery_partner_profiles` | One per delivery partner: vehicle type, registration number, service city and PIN |
| `categories` | Assignment table; soft delete via `status`; optional `parent_category_id` for subcategories |
| `products` | Belongs to a category and a seller. Price, discount, selling price, stock, `approval_status`, `rejection_reason`, `sold_count`, rating |
| `product_images` / `product_specifications` | Gallery photos and specification rows (many per product) |
| `carts` / `cart_items` | One cart per customer; `price_at_add` for price-change notices; saved-for-later flag |
| `wishlist_items` | Saved products; `(user_id, product_id)` unique |
| `addresses` | Customer address book with one default |
| `orders` / `order_items` | Address, product and price **snapshots**, coupon, payment method. Each item has its own `item_status`, courier and tracking number, and `delivery_partner_id` / `assigned_at` when a ShopSphere partner delivers it |
| `payments` | Method (RAZORPAY/COD), status, Razorpay IDs, `refunded_amount`. Card data is never stored |
| `return_requests` | One per order item: reason, status, refund amount, the seller who decides |
| `reviews` | One review per customer per product; `(product_id, user_id)` unique |
| `coupons` / `coupon_redemptions` | Discount rules; one redemption per customer, linked to the order |
| `order_item_events` | Tracking history: one row per status change, with a plain-words description and time |
| `notifications` | In-app notifications per user with a link and read flag |
| `contact_messages` | Contact-form messages for the admin inbox |
| `platform_settings` | Single row of business rules edited by the admin |

Money uses `DECIMAL(12,2)`. Products and categories are never hard-deleted, so order history always stays intact.

---

## Tests

```bash
cd Backend/ecommerce && ./mvnw test
```
There are **54 tests**: role-based registration (customer, seller, delivery partner and admin access-code rules, vehicle-number formats), Category Management rules (including subcategory cycles), OTP security, Razorpay checkout and webhook signatures, idempotent payment verification, the sold-out refund path, coupon rules (caps, minimum order, expiry, usage limits, one use per customer), verified-purchase reviews, pricing, shipping and commission rules, COD checkout, and item-level order status derivation. The `contextLoads` test needs MySQL running.

Frontend: `npm run lint` and `npm run build`.

A live-tracking run covered 13 checks: the customer's and partner's streams receive each update within about 30–40 ms, the partner is told when a parcel is packed, and the 5-step tracking history is recorded in order. An order-journey run covered 27 checks: COD order → seller confirm and pack → partner accept, with the customer's contact hidden until then and a second accept rejected → out for delivery → delivered and paid, plus notifications and role isolation. A catalogue and demo run covered 26 checks: 77 products across 6 stores, photos, specs and gallery, search, demo sign-in for all four roles, and admin registration including its rules. A route smoke test opened all 57 pages as guest, customer, seller, delivery partner and admin. A role-registration end-to-end run covered 23 checks: per-role validation, sign-up and OTP for each role, login for all four roles, role isolation, admin review and notifications. An earlier end-to-end API run covered 56 checks: COD checkout, seller fulfilment with tracking, return and refund, notifications, uploads (including rejecting a disguised script and blocking path traversal), product approval and re-approval, settings, reports, the contact inbox and role-based access.

---

## Deployment notes
- Set secrets as **environment variables**, for example `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_APP_JWTSECRET`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD`, `APP_ADMIN_REGISTRATION_CODE`, `APP_FRONTEND_URLS` and `APP_FRONTEND_PUBLIC_URL`.
- Set `APP_SEED_DEMO_DATA=false` in production.
- Frontend: `VITE_API_BASE_URL=https://your-api/api npm run build`, then host `dist/` with SPA fallback so every route serves `index.html`.

## Remaining work
- Delivery partner earnings, route planning and proof of delivery (OTP or photo)
- Guest cart (today guests are asked to sign in before adding to cart) and merging it after login
- Moving uploaded images to object storage (S3/Cloudinary) for multi-server hosting
- Automated payouts to sellers (earnings are reported; settlement is manual)
- Deployment (handled separately)
