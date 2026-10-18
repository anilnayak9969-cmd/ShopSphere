-- =====================================================================
-- ShopSphere — useful SQL queries (MySQL)
-- These are the SQL equivalents of what the Spring Boot app does through
-- JPA, handy for viva/mock evaluation and for checking data in Workbench.
-- =====================================================================

USE ecommerce_intern;

-- =====================================================================
-- 1. CATEGORY MANAGEMENT MODULE (assignment)
-- =====================================================================

-- 1.1 Create a new category (category_name is UNIQUE)
INSERT INTO categories (category_name, description, status, created_at, updated_at)
VALUES ('Home Decor', 'Lamps, rugs, vases and cushions for every room.', b'1', NOW(6), NOW(6));

-- 1.2 Category dashboard: every category with its product count
--     (LEFT JOIN so categories with 0 products are still listed)
SELECT c.category_id,
       c.category_name,
       c.description,
       IF(c.status, 'Active', 'Inactive')              AS status,
       COUNT(p.product_id)                              AS total_products,
       COALESCE(SUM(p.active = b'1'), 0)                AS active_products,
       c.created_at,
       c.updated_at
FROM categories c
LEFT JOIN products p ON p.category_id = c.category_id
GROUP BY c.category_id
ORDER BY c.category_name;

-- 1.3 Search categories by name (case-insensitive) with pagination (page 1, 10 per page)
SELECT * FROM categories
WHERE LOWER(category_name) LIKE CONCAT('%', LOWER('aud'), '%')
ORDER BY category_name
LIMIT 10 OFFSET 0;

-- 1.4 Update a category's name or description
UPDATE categories
SET category_name = 'Home & Living',
    description   = 'Lamps, rugs, vases, cushions and more.',
    updated_at    = NOW(6)
WHERE category_name = 'Home Decor';

-- 1.5 Before deactivating: how many ACTIVE products would be affected?
SELECT COUNT(*) AS active_products
FROM products
WHERE category_id = (SELECT category_id FROM categories WHERE category_name = 'Home & Living')
  AND active = b'1';

-- 1.6 Reassign all products from one category to another (do this before deactivating)
--     UPDATE products SET category_id = <target_id>, updated_at = NOW(6) WHERE category_id = <source_id>;

-- 1.7 Soft delete (deactivate) — the row is kept so old orders keep their category
UPDATE categories SET status = b'0', updated_at = NOW(6) WHERE category_name = 'Home & Living';

-- 1.8 Reactivate
UPDATE categories SET status = b'1', updated_at = NOW(6) WHERE category_name = 'Home & Living';

-- 1.9 Categories the storefront shows (active only)
SELECT category_id, category_name FROM categories WHERE status = b'1' ORDER BY category_name;

-- Clean up the example rows created above
DELETE FROM categories
WHERE category_name = 'Home & Living'
  AND category_id NOT IN (SELECT category_id FROM (SELECT category_id FROM products) AS used);

-- =====================================================================
-- 2. USERS AND SELLERS
-- =====================================================================

-- 2.1 Accounts by role
SELECT role, COUNT(*) AS accounts FROM users GROUP BY role;

-- 2.2 Seller applications waiting for approval
SELECT user_id, store_name, full_name, email, phone, created_at
FROM users
WHERE role = 'SELLER' AND seller_status = 'PENDING'
ORDER BY created_at;

-- 2.3 Approve a seller
-- UPDATE users SET seller_status = 'APPROVED', updated_at = NOW(6) WHERE user_id = <id> AND role = 'SELLER';

-- =====================================================================
-- 3. PRODUCTS AND INVENTORY
-- =====================================================================

-- 3.1 Storefront listing: what customers can actually see
SELECT p.product_id, p.product_name, c.category_name, u.store_name,
       p.price, p.discount_percent, p.selling_price, p.stock_quantity,
       p.rating_average, p.rating_count
FROM products p
JOIN categories c ON c.category_id = p.category_id
JOIN users u      ON u.user_id     = p.seller_id
WHERE p.active = b'1' AND c.status = b'1'
  AND u.active = b'1' AND u.seller_status = 'APPROVED'
ORDER BY p.created_at DESC;

-- 3.2 Low stock report (5 units or fewer)
SELECT u.store_name, p.sku, p.product_name, p.stock_quantity
FROM products p JOIN users u ON u.user_id = p.seller_id
WHERE p.stock_quantity <= 5
ORDER BY p.stock_quantity, u.store_name;

-- 3.3 Top-rated products (at least one review)
SELECT product_name, rating_average, rating_count
FROM products
WHERE rating_count > 0
ORDER BY rating_average DESC, rating_count DESC
LIMIT 10;

-- =====================================================================
-- 4. ORDERS, PAYMENTS AND REVENUE
-- =====================================================================

-- 4.1 Orders with their latest payment status
SELECT o.order_number, u.email AS customer, o.status, o.subtotal, o.discount_amount,
       o.shipping_fee, o.total_amount, pay.status AS payment_status, o.created_at
FROM orders o
JOIN users u ON u.user_id = o.customer_id
LEFT JOIN payments pay ON pay.payment_id = (
    SELECT MAX(p2.payment_id) FROM payments p2 WHERE p2.order_id = o.order_id)
ORDER BY o.created_at DESC;

-- 4.2 Revenue per day (successful Razorpay payments)
SELECT DATE(created_at) AS day, COUNT(*) AS payments, SUM(amount) AS revenue
FROM payments
WHERE status = 'SUCCESS'
GROUP BY DATE(created_at)
ORDER BY day DESC;

-- 4.3 Revenue per category (paid orders only)
SELECT c.category_name, SUM(oi.quantity) AS units_sold, SUM(oi.line_total) AS revenue
FROM order_items oi
JOIN orders o     ON o.order_id = oi.order_id
JOIN products p   ON p.product_id = oi.product_id
JOIN categories c ON c.category_id = p.category_id
WHERE o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'RETURNED')
GROUP BY c.category_id
ORDER BY revenue DESC;

-- 4.4 Seller earnings
SELECT u.store_name, COUNT(DISTINCT oi.order_id) AS orders, SUM(oi.quantity) AS units, SUM(oi.line_total) AS revenue
FROM order_items oi
JOIN orders o ON o.order_id = oi.order_id
JOIN users u  ON u.user_id = oi.seller_id
WHERE o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'RETURNED')
GROUP BY u.user_id
ORDER BY revenue DESC;

-- 4.5 Best-selling products
SELECT oi.product_name, SUM(oi.quantity) AS units_sold
FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
WHERE o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'RETURNED')
GROUP BY oi.product_id, oi.product_name
ORDER BY units_sold DESC
LIMIT 10;

-- =====================================================================
-- 5. CART, WISHLIST, REVIEWS, COUPONS
-- =====================================================================

-- 5.1 Cart contents of a customer with live prices
SELECT u.email, p.product_name, ci.quantity, p.selling_price, ci.quantity * p.selling_price AS line_total
FROM cart_items ci
JOIN carts c    ON c.cart_id = ci.cart_id
JOIN users u    ON u.user_id = c.user_id
JOIN products p ON p.product_id = ci.product_id
ORDER BY u.email;

-- 5.2 Most wish-listed products
SELECT p.product_name, COUNT(*) AS saved_by
FROM wishlist_items w JOIN products p ON p.product_id = w.product_id
GROUP BY p.product_id
ORDER BY saved_by DESC
LIMIT 10;

-- 5.3 Latest reviews
SELECT p.product_name, r.rating, r.title, u.full_name, r.created_at
FROM reviews r
JOIN products p ON p.product_id = r.product_id
JOIN users u    ON u.user_id = r.user_id
ORDER BY r.created_at DESC
LIMIT 20;

-- 5.4 Coupon performance
SELECT c.code, c.discount_type, c.discount_value, c.used_count, c.usage_limit,
       COALESCE(SUM(cr.discount_amount), 0) AS total_discount_given
FROM coupons c
LEFT JOIN coupon_redemptions cr ON cr.coupon_id = c.coupon_id
GROUP BY c.coupon_id
ORDER BY c.used_count DESC;

-- =====================================================================
-- 6. CATEGORY TREE, PRODUCT APPROVAL, FULFILMENT, RETURNS (v3)
-- =====================================================================

-- 6.1 Category tree: departments with their subcategories
SELECT parent.category_name AS department, child.category_name AS subcategory, child.status
FROM categories parent
LEFT JOIN categories child ON child.parent_category_id = parent.category_id
WHERE parent.parent_category_id IS NULL
ORDER BY parent.category_name, child.category_name;

-- 6.2 Product approval queue (oldest first)
SELECT p.product_id, p.product_name, u.store_name, p.approval_status, p.updated_at
FROM products p JOIN users u ON u.user_id = p.seller_id
WHERE p.approval_status = 'PENDING'
ORDER BY p.updated_at;

-- 6.3 Approve / reject a product (the app also notifies the seller)
-- UPDATE products SET approval_status = 'APPROVED', rejection_reason = NULL, updated_at = NOW(6) WHERE product_id = <id>;
-- UPDATE products SET approval_status = 'REJECTED', rejection_reason = 'Photo is blurry', updated_at = NOW(6) WHERE product_id = <id>;

-- 6.4 Items a seller still has to ship (item-level fulfilment)
SELECT o.order_number, oi.product_name, oi.quantity, oi.item_status, o.payment_method, o.city, o.created_at
FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
WHERE oi.seller_id = 2                                    -- the seller's user_id
  AND oi.item_status IN ('PLACED', 'CONFIRMED', 'PACKED')
ORDER BY o.created_at;

-- 6.5 Shipment tracking for an order
SELECT oi.product_name, oi.item_status, oi.courier_name, oi.tracking_number, oi.shipped_at, oi.delivered_at
FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
WHERE o.order_number = 'SS261016-ABC123';                 -- replace with a real order number

-- 6.6 Payments by method and status (COD stays PENDING until delivery)
SELECT method, status, COUNT(*) AS payments, SUM(amount) AS amount, SUM(refunded_amount) AS refunded
FROM payments
GROUP BY method, status
ORDER BY method, status;

-- 6.7 Open return requests with the store that has to decide
SELECT rr.return_id, o.order_number, oi.product_name, rr.reason, rr.comments, u.store_name, rr.created_at
FROM return_requests rr
JOIN order_items oi ON oi.order_item_id = rr.order_item_id
JOIN orders o       ON o.order_id = oi.order_id
JOIN users u        ON u.user_id = rr.seller_id
WHERE rr.status = 'REQUESTED'
ORDER BY rr.created_at;

-- 6.8 Seller commission statement per month (delivered items only; rate from platform_settings)
SELECT YEAR(oi.delivered_at) AS yr, MONTH(oi.delivered_at) AS mon,
       SUM(oi.quantity) AS units, SUM(oi.line_total) AS gross_sales,
       ROUND(SUM(oi.line_total) * ps.commission_percent / 100, 2) AS commission,
       SUM(oi.line_total) - ROUND(SUM(oi.line_total) * ps.commission_percent / 100, 2) AS net_earnings
FROM order_items oi
CROSS JOIN platform_settings ps
WHERE oi.seller_id = 2 AND oi.item_status = 'DELIVERED' AND ps.settings_id = 1
GROUP BY yr, mon, ps.commission_percent
ORDER BY yr DESC, mon DESC;

-- 6.9 Platform settings used at checkout
SELECT commission_percent, free_shipping_threshold, shipping_fee, cod_enabled, cod_max_amount,
       return_window_days, low_stock_threshold
FROM platform_settings WHERE settings_id = 1;

-- 6.10 Unread notifications per user, and unresolved contact messages
SELECT u.email, COUNT(*) AS unread
FROM notifications n JOIN users u ON u.user_id = n.user_id
WHERE n.is_read = b'0'
GROUP BY u.user_id;

SELECT message_id, name, email, subject, created_at FROM contact_messages WHERE resolved = b'0' ORDER BY created_at;

-- =====================================================================
-- 7. ROLES: SELLER AND DELIVERY PARTNER APPLICATIONS
-- =====================================================================

-- 7.1 Accounts per role and application status
SELECT role, COALESCE(seller_status, '-') AS application_status, COUNT(*) AS accounts
FROM users
GROUP BY role, seller_status
ORDER BY role, application_status;

-- 7.2 Seller applications with their business details
SELECT u.store_name, u.full_name, u.email, sp.business_type, sp.gstin, sp.city, sp.state, u.seller_status
FROM users u LEFT JOIN seller_profiles sp ON sp.user_id = u.user_id
WHERE u.role = 'SELLER'
ORDER BY u.created_at DESC;

-- 7.3 Delivery partner applications waiting for review
SELECT u.full_name, u.email, u.phone, dp.vehicle_type, dp.vehicle_number, dp.service_city, dp.service_pincode, u.created_at
FROM users u JOIN delivery_partner_profiles dp ON dp.user_id = u.user_id
WHERE u.role = 'DELIVERY_PARTNER' AND u.seller_status = 'PENDING'
ORDER BY u.created_at;

-- 7.4 Approve a delivery partner (the app also notifies them)
-- UPDATE users SET seller_status = 'APPROVED', updated_at = NOW(6) WHERE user_id = <id> AND role = 'DELIVERY_PARTNER';

-- =====================================================================
-- 8. USERS — view, search, update and delete accounts
-- =====================================================================

-- 8.1 All users (newest first). Passwords are BCrypt hashes, so they are never selected.
SELECT user_id, full_name, email, phone, role, seller_status, store_name,
       email_verified + 0 AS email_verified,  -- BIT(1) shown as 1 / 0
       active + 0 AS active,
       created_at
FROM users
ORDER BY created_at DESC;

-- 8.2 How many accounts per role
SELECT role, COUNT(*) AS accounts,
       SUM(active = b'1') AS active_accounts,
       SUM(email_verified = b'1') AS verified_emails
FROM users
GROUP BY role
ORDER BY role;

-- 8.3 Find a user by email, name or phone
SELECT user_id, full_name, email, phone, role, active + 0 AS active
FROM users
WHERE email LIKE '%nayak%' OR full_name LIKE '%nayak%' OR phone LIKE '%9969%';

-- 8.4 Customers with their order count and total spent (cancelled orders excluded)
SELECT u.user_id, u.full_name, u.email,
       COUNT(o.order_id) AS orders,
       COALESCE(SUM(o.total_amount), 0) AS total_spent
FROM users u
LEFT JOIN orders o ON o.customer_id = u.user_id AND o.status NOT IN ('CANCELLED', 'PENDING_PAYMENT')
WHERE u.role = 'CUSTOMER'
GROUP BY u.user_id, u.full_name, u.email
ORDER BY total_spent DESC;

-- 8.5 Sellers with their store details and number of products
SELECT u.user_id, u.store_name, u.full_name, u.email, u.seller_status,
       sp.business_type, sp.city, sp.state,
       COUNT(p.product_id) AS products
FROM users u
LEFT JOIN seller_profiles sp ON sp.user_id = u.user_id
LEFT JOIN products p ON p.seller_id = u.user_id
WHERE u.role = 'SELLER'
GROUP BY u.user_id, u.store_name, u.full_name, u.email, u.seller_status, sp.business_type, sp.city, sp.state
ORDER BY u.store_name;

-- 8.6 Delivery partners with vehicle, area and deliveries completed
SELECT u.user_id, u.full_name, u.phone, u.seller_status AS application_status,
       dp.vehicle_type, dp.vehicle_number, dp.service_city,
       COUNT(oi.order_item_id) AS delivered
FROM users u
JOIN delivery_partner_profiles dp ON dp.user_id = u.user_id
LEFT JOIN order_items oi ON oi.delivery_partner_id = u.user_id AND oi.item_status = 'DELIVERED'
WHERE u.role = 'DELIVERY_PARTNER'
GROUP BY u.user_id, u.full_name, u.phone, u.seller_status, dp.vehicle_type, dp.vehicle_number, dp.service_city;

-- 8.7 Accounts that never verified their email (registered but did not enter the OTP)
SELECT user_id, full_name, email, role, created_at
FROM users
WHERE email_verified = b'0'
ORDER BY created_at;

-- 8.8 Suspended accounts
SELECT user_id, full_name, email, role FROM users WHERE active = b'0';

-- 8.9 New sign-ups in the last 7 days
SELECT DATE(created_at) AS day, role, COUNT(*) AS new_accounts
FROM users
WHERE created_at >= NOW() - INTERVAL 7 DAY
GROUP BY DATE(created_at), role
ORDER BY day DESC, role;

-- 8.10 Update examples (the admin console does the same; uncomment and set the id)
-- Suspend / reactivate an account:
-- UPDATE users SET active = b'0', updated_at = NOW(6) WHERE user_id = <id> AND role <> 'ADMIN';
-- UPDATE users SET active = b'1', updated_at = NOW(6) WHERE user_id = <id>;
-- Approve a seller or delivery partner application:
-- UPDATE users SET seller_status = 'APPROVED', updated_at = NOW(6) WHERE user_id = <id> AND role IN ('SELLER', 'DELIVERY_PARTNER');
-- Change a phone number:
-- UPDATE users SET phone = '9876543210', updated_at = NOW(6) WHERE user_id = <id>;

-- 8.11 Delete a user safely (the script used to remove user 13).
-- Rows that point to the user must go first, or MySQL refuses the delete (foreign keys).
-- Only for accounts WITHOUT orders: users who bought or sold something should be suspended (8.10)
-- instead, so order history, invoices and payouts stay correct. Check first:
--   SELECT (SELECT COUNT(*) FROM orders WHERE customer_id = <id>)
--        + (SELECT COUNT(*) FROM order_items WHERE seller_id = <id> OR delivery_partner_id = <id>) AS linked_orders;
--
-- START TRANSACTION;
-- SET @uid := <id>;
-- SET @email := (SELECT email FROM users WHERE user_id = @uid);
-- DELETE FROM product_specifications WHERE product_id IN (SELECT product_id FROM (SELECT product_id FROM products WHERE seller_id = @uid) p);
-- DELETE FROM product_images         WHERE product_id IN (SELECT product_id FROM (SELECT product_id FROM products WHERE seller_id = @uid) p);
-- DELETE FROM cart_items             WHERE product_id IN (SELECT product_id FROM (SELECT product_id FROM products WHERE seller_id = @uid) p);
-- DELETE FROM wishlist_items         WHERE product_id IN (SELECT product_id FROM (SELECT product_id FROM products WHERE seller_id = @uid) p);
-- DELETE FROM products               WHERE seller_id = @uid;
-- DELETE FROM seller_profiles        WHERE user_id = @uid;
-- DELETE FROM delivery_partner_profiles WHERE user_id = @uid;
-- DELETE ci FROM cart_items ci JOIN carts c ON c.cart_id = ci.cart_id WHERE c.user_id = @uid;
-- DELETE FROM carts                  WHERE user_id = @uid;
-- DELETE FROM wishlist_items         WHERE user_id = @uid;
-- DELETE FROM addresses              WHERE user_id = @uid;
-- DELETE FROM reviews                WHERE user_id = @uid;
-- DELETE FROM notifications          WHERE user_id = @uid;
-- DELETE FROM email_otps             WHERE email = @email;
-- DELETE FROM users                  WHERE user_id = @uid;
-- COMMIT;
