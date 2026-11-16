# ShopSphere — Product Management Module

Admin module for managing the store's inventory: add, view, update and deactivate products while tracking stock levels, prices, descriptions and status. Built for the ITVedant / Vibgyor Advisors integrated internship.

**Submission document:** [Anil_Nayak_ShopSphere - Product Management Module Assignment Submission.pdf](Anil_Nayak_ShopSphere%20-%20Product%20Management%20Module%20Assignment%20Submission.pdf). Its source is [Product_Management_Module.html](Product_Management_Module.html), and the screenshots are in [screenshots/](screenshots/).

---

## Requirement checklist

| # | Requirement | Where it is implemented | Status |
|---|-------------|-------------------------|--------|
| 1 | **Add a new product.** Only admins. Fields: name, description, price, category, SKU, inventory count | **Admin → Products → New product** (`AdminProductForm.jsx`) → `POST /api/admin/products`. ADMIN role only (others get HTTP 403). Admin-added products are approved and live straight away | ✅ Done |
| 2 | **Product dashboard.** View all products; create, update and delete options; stock, price and status | **Admin → Products** (`AdminProducts.jsx`): summary cards (total, active, inactive, low stock), a table with price, stock, category, store and Active/Inactive status, search, filters, pagination, and **New product**, **Edit** and **Deactivate/Activate** buttons | ✅ Done |
| 3 | **Update product details.** Price, description, inventory count, category | **Edit** → `PUT /api/admin/products/{id}`. Changes go live at once, and the seller is notified | ✅ Done |
| 4 | **Delete / deactivate.** Soft delete through a Boolean status column, with a warning first | **Deactivate** → `PATCH /api/admin/products/{id}/status` `{"active": false}`. A warning says the product will be hidden from the customer interface. The row is never deleted. **Activate** restores it | ✅ Done |
| — | **Database design:** table `products` | All assignment columns exist (see below) | ✅ Done |

---

## How to use it

1. Start MySQL, the backend (`Backend/ecommerce`: `mvnw.cmd spring-boot:run`) and the frontend (`Frontend/ecommerce`: `npm run dev`).
2. Open http://localhost:5173/login, choose the **Admin** tab and sign in. On a local setup, **Try a demo account → Admin** signs in with one click.
3. Click **Products** in the left menu.

| Task | Steps |
|------|-------|
| Add a product | **New product** → choose the store (*Sold by*), then fill in name, description, category, SKU, price, discount, inventory count and an optional brand and photo → **Add product** |
| Find a product | Type a name, brand, SKU or category in the search box; filter by category, by **All / Active / Inactive**, or by approval tab |
| Update a product | **Edit** in its row → change price, description, inventory count, category and so on → **Save changes** |
| Deactivate (soft delete) | **Deactivate** in its row → read the warning → **Deactivate**. The product is hidden from customers and keeps its history |
| Reactivate | **Inactive** filter → **Activate** → **Activate** |
| Approve seller listings | **Approval queue** tab → **Review** → **Approve**, or **Reject…** with a reason |

### Validation rules

| Field | Rule |
|-------|------|
| Sold by | Required when adding; the store must be approved and active |
| Product name | 2–150 characters |
| Description | 10–2000 characters |
| Category | Required; must be active (a product may keep its current category if that category is later deactivated) |
| SKU | 3–60 letters, numbers, `-` or `_`; unique (case-insensitive); saved in capitals |
| Price | ₹1 to ₹1,00,00,000, at most 2 decimal places |
| Discount | 0–90% (selling price is calculated on the server) |
| Inventory count | Whole number from 0 to 100000 |
| Photo | Optional; JPG, PNG or WEBP up to 5 MB, or an `http(s)://` link |
| Activate | Only while the product's category is active |

---

## Database design

Table `products` (MySQL database `ecommerce_intern`):

| Assignment column | Assignment type | ShopSphere column | Notes |
|-------------------|-----------------|-------------------|-------|
| `product_id` | int (pk, auto_increment) | `product_id` BIGINT PK AUTO_INCREMENT | |
| `product_name` | varchar(150) | `product_name` VARCHAR(150) | |
| `description` | varchar(500) | `description` VARCHAR(2000) | Longer limit for full product details |
| `price` | decimal(10,2) | `price` DECIMAL(12,2) | MRP; `selling_price` = price after `discount_percent` |
| `SKU` | varchar(50) | `sku` VARCHAR(60) UNIQUE | |
| `category_id` | int (fk) | `category_id` INT FK → `categories` | |
| `inventory_count` | int | `stock_quantity` INT | Same meaning, ShopSphere name |
| `created_at` | datetime | `created_at` DATETIME(6) | |
| `updated_at` | datetime | `updated_at` DATETIME(6) | |
| `status` | boolean | `active` BIT(1) | 1 = active, 0 = inactive (soft delete) |

The marketplace adds `seller_id`, `brand`, `discount_percent`, `selling_price`, `image_url`, `approval_status`, `rejection_reason`, `rating_average`, `rating_count` and `sold_count`.

SQL for every action (add, dashboard, summary, search, update, deactivate, reactivate) is in [`DataBase/queries.sql`](../../DataBase/queries.sql), section 7. It was tested against the local database inside a transaction that was rolled back.

---

## API

All endpoints need an admin JWT: `Authorization: Bearer <token>`.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/products?search=&categoryId=&approval=&active=&page=&size=` | Dashboard list (`active=true/false` filters by status) |
| GET | `/api/admin/products/summary` | `{ total, active, inactive, awaitingApproval, lowStock, lowStockThreshold }` |
| GET | `/api/admin/products/{id}` | One product (for the edit form) |
| POST | `/api/admin/products` | Add: `{ "sellerId": 4, "product": { "productName", "description", "sku", "price", "discountPercent", "stockQuantity", "categoryId", "brand", "imageUrl" } }` |
| PUT | `/api/admin/products/{id}` | Update details (same `product` fields) |
| PATCH | `/api/admin/products/{id}/status` | `{"active": false}` deactivate · `{"active": true}` reactivate |
| PATCH | `/api/admin/products/{id}/approve` · `/reject` · `/category` | Approval queue and category moves |

Example: add a product with `curl` (replace `$TOKEN`):

```bash
curl -X POST http://localhost:8080/api/admin/products \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"sellerId":4,"product":{"productName":"Linen Table Lamp","description":"Ceramic table lamp with a natural linen shade.","sku":"LAMP-LINEN-01","price":1899,"discountPercent":10,"stockQuantity":30,"categoryId":21}}'
```

---

## Code map

| Layer | Files |
|-------|-------|
| Controller | `Backend/ecommerce/src/main/java/com/example/ecommerce/controller/AdminController.java` (product section) |
| Service | `services/ProductService.java`, `services/impl/ProductServiceImpl.java` (`adminProducts`, `adminSummary`, `adminProduct`, `adminCreateProduct`, `adminUpdateProduct`, `adminSetActive`) |
| DTOs | `dtos/product/AdminProductRequest.java`, `ProductRequest.java` (validation), `ProductResponse.java`, `ProductSummaryResponse.java` |
| Entity / queries | `entites/Product.java`, `repositories/ProductRepository.java`, `repositories/ProductSpecifications.java` (search and filters, including `active`) |
| Security | `config/SecurityConfig.java`: `/api/admin/**` requires the ADMIN role |
| Frontend | `Frontend/ecommerce/src/pages/admin/AdminProducts.jsx` (dashboard), `AdminProductForm.jsx` (add/edit), `src/services/adminService.js`, routes in `src/App.jsx` (`/admin/products`, `/admin/products/new`, `/admin/products/:id/edit`) |
| Tests | `Backend/ecommerce/src/test/java/com/example/ecommerce/services/impl/ProductServiceImplAdminTest.java` (7 tests) |

Run the module tests:

```bash
cd Backend/ecommerce
mvnw.cmd test -Dtest=ProductServiceImplAdminTest
```
