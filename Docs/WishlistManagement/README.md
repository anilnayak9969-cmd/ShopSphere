# ShopSphere — Wishlist Management Module

Customers save products they like but are not ready to buy, see their price and availability, remove them, and move them to the cart. Built for the ITVedant / Vibgyor Advisors integrated internship.

**Submission document:** [Anil_Nayak_ShopSphere - Wishlist Management Module Assignment Submission.pdf](Anil_Nayak_ShopSphere%20-%20Wishlist%20Management%20Module%20Assignment%20Submission.pdf) · source [Wishlist_Management_Module.html](Wishlist_Management_Module.html) · [screenshots/](screenshots/)

## Requirement checklist

| # | Requirement | Implementation | Status |
|---|-------------|----------------|--------|
| 1 | **Add to wishlist** from the product page | Heart / **Save to wishlist** on product page and cards → `POST /api/customer/wishlist/{productId}` (on-sale products only, once per customer, max 100) | ✅ Done |
| 2 | **View wishlist**: name, price, availability | **Account → Wishlist**: name, price, discount, **availability badge** (In stock / Only N left / Out of stock / No longer available) and saved date — `GET /api/customer/wishlist` | ✅ Done (availability + date **added**) |
| 3 | **Remove from wishlist** | Bin button or heart → `DELETE /api/customer/wishlist/{productId}` | ✅ Done |
| 4 | **Move from wishlist to cart** | **Move to cart** → `POST /api/customer/wishlist/{productId}/move-to-cart` (one transaction: add 1 to cart with stock check, then remove) | ✅ Done (single server step **added**) |
| — | **Database**: `wishlists` | `wishlist_items` (`wishlist_item_id`, `user_id`, `product_id`, `created_at`, `updated_at`; unique `(user_id, product_id)`) | ✅ Done (`updated_at` **added**) |

## How to use it

Sign in as a customer → open a product → **Save to wishlist** → header **Wishlist** → **Move to cart** or the bin button. From the cart, **Save for later** moves an item back to the wishlist.

## Database

| Assignment | ShopSphere (`wishlist_items`) |
|------------|-------------------------------|
| `wishlist_id` | `wishlist_item_id` BIGINT PK |
| `customer_id` | `user_id` FK → `users` |
| `product_id` | `product_id` FK → `products` |
| `created_at` | `created_at` DATETIME(6) |
| `updated_at` | `updated_at` DATETIME(6) |

SQL for every action: [`DataBase/queries.sql`](../../DataBase/queries.sql), section 12 (tested in a rolled-back transaction).

## Code map

| Layer | Files |
|-------|-------|
| Backend | `services/impl/WishlistServiceImpl.java`, `services/impl/CartServiceImpl.java` (`moveFromWishlist`, one transaction), `controller/CustomerAccountController.java` (`move-to-cart` endpoint), `entites/WishlistItem.java`, `dtos/wishlist/WishlistItemResponse.java` (availability), `repositories/WishlistItemRepository.java` |
| Frontend | `src/components/WishlistButton.jsx`, `src/context/WishlistContext.jsx` (`moveToCart`), `src/pages/customer/Wishlist.jsx`, `src/services/wishlistService.js` |
| Tests | `WishlistManagementTest` (10 tests) |

## Testing

**Unit tests** — `WishlistManagementTest` (10, all passing):

- adding a product that is on sale saves it; adding it twice keeps one entry
- products not on sale are refused; the wishlist holds at most 100 products
- the view shows name, price and the four availability states (In stock / Only N left / Out of stock / No longer available)
- remove deletes the entry, and removing something not saved is harmless
- move to cart adds 1 unit and removes it from the wishlist
- move to cart of an out-of-stock product fails and leaves the wishlist unchanged
- move to cart of a product that is not saved is refused

**End-to-end checks** against the running app (11 wishlist checks, all passing): add, add twice, unknown product (404), view with availability, out-of-stock label, out-of-stock move refused with the wishlist unchanged (409), move of a product not saved (404), successful move, remove, seller refused (403), signed-out user refused (401).

```bash
cd Backend/ecommerce
mvnw.cmd test -Dtest=WishlistManagementTest
```
