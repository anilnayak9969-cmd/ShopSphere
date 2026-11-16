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

-- =====================================================================
-- 7. PRODUCT MANAGEMENT MODULE (assignment)
--    Assignment column  ->  ShopSphere column
--    inventory_count    ->  stock_quantity      status (boolean) -> active BIT(1)
--    SKU                ->  sku (UNIQUE)        price            -> price DECIMAL(12,2)
-- =====================================================================

-- 7.1 Add a new product (admin). Admin-added products are approved straight away.
--     selling_price = price minus discount, stored so the shop can sort and filter on it.
INSERT INTO products (product_name, description, sku, brand, price, discount_percent, selling_price,
                      stock_quantity, image_url, active, category_id, seller_id, approval_status,
                      rating_average, rating_count, sold_count, created_at, updated_at)
VALUES ('Linen Table Lamp (SQL example)', 'Ceramic table lamp with a natural linen shade for bedside tables.',
        'SQL-LAMP-001', 'UrbanNest', 1899.00, 10, 1709.10, 30, NULL, b'1',
        (SELECT category_id FROM categories WHERE category_name = 'Home Décor'),
        (SELECT user_id FROM users WHERE email = 'urbannest@shopsphere.local'),
        'APPROVED', 0.00, 0, 0, NOW(6), NOW(6));

-- 7.2 Product dashboard: every product with category, store, price, stock and status
SELECT p.product_id,
       p.product_name,
       p.sku,
       c.category_name,
       u.store_name,
       p.price,
       p.selling_price,
       p.stock_quantity                              AS inventory_count,
       IF(p.active, 'Active', 'Inactive')            AS status,
       p.approval_status,
       p.updated_at
FROM products p
JOIN categories c ON c.category_id = p.category_id
JOIN users u      ON u.user_id     = p.seller_id
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 0;

-- 7.3 Summary cards: total, active, inactive and low stock (threshold from platform_settings)
SELECT COUNT(*)                                                         AS total_products,
       SUM(active = b'1')                                               AS active_products,
       SUM(active = b'0')                                               AS inactive_products,
       SUM(active = b'1' AND stock_quantity <= (SELECT low_stock_threshold FROM platform_settings LIMIT 1)) AS low_stock
FROM products;

-- 7.4 Search by name, brand, SKU or category, and filter by status (the dashboard search box)
SELECT p.product_id, p.product_name, p.sku, p.stock_quantity, p.active + 0 AS active
FROM products p
JOIN categories c ON c.category_id = p.category_id
WHERE (LOWER(p.product_name) LIKE '%lamp%' OR LOWER(p.sku) LIKE '%lamp%'
       OR LOWER(p.brand) LIKE '%lamp%' OR LOWER(c.category_name) LIKE '%lamp%')
  AND p.active = b'1';

-- 7.5 Update product details: price, description, inventory count and category
UPDATE products
SET price            = 1799.00,
    discount_percent = 10,
    selling_price    = 1619.10,
    description      = 'Ceramic table lamp with a natural linen shade. Takes one E27 bulb.',
    stock_quantity   = 12,
    category_id      = (SELECT category_id FROM (SELECT category_id FROM categories WHERE category_name = 'Home Décor') AS c),
    updated_at       = NOW(6)
WHERE sku = 'SQL-LAMP-001';

-- 7.6 Deactivate (soft delete): the row stays, customers no longer see the product
UPDATE products SET active = b'0', updated_at = NOW(6) WHERE sku = 'SQL-LAMP-001';

-- 7.7 Reactivate
UPDATE products SET active = b'1', updated_at = NOW(6) WHERE sku = 'SQL-LAMP-001';

-- 7.8 Inactive (soft-deleted) products
SELECT product_id, product_name, sku, updated_at FROM products WHERE active = b'0';

-- Clean up the example row created above (only if it was never ordered)
DELETE FROM products
WHERE sku = 'SQL-LAMP-001'
  AND product_id NOT IN (SELECT product_id FROM (SELECT product_id FROM order_items) AS used);
-- =====================================================================
-- 8. ORDER MANAGEMENT MODULE (assignment)
--    Assignment column  ->  ShopSphere column
--    order id           ->  order_id (+ readable order_number, e.g. SS261116-P8Q68X)
--    order_status       ->  status VARCHAR(30)   (PLACED = "Pending", SHIPPED, DELIVERED, CANCELLED, ...)
--    shipping_address   ->  shipping_name, address_line1, address_line2, city, state, pincode
--    status (boolean)   ->  active BIT(1)        (1 = active, 0 = cancelled; soft delete)
--    Each order has order_items (one row per product) with their own item_status.
-- =====================================================================

-- 8.1 Place an order: the total is items - coupon + shipping; GST (18%) is already inside the prices.
--     (ShopSphere does this in one transaction at checkout; shown here step by step.)
SET @customer := (SELECT user_id FROM users WHERE email = 'demo.customer@shopsphere.local');
SET @product  := 62;   -- Tales from the Western Ghats (paperback)
SET @qty      := 1;
SET @items    := (SELECT selling_price * @qty FROM products WHERE product_id = @product);
SET @shipping := (SELECT IF(@items >= free_shipping_threshold, 0, shipping_fee) FROM platform_settings LIMIT 1);
SELECT @items AS items_total, @shipping AS shipping_fee, ROUND(@items + @shipping, 2) AS total_amount,
       ROUND(@items * 18 / 118, 2) AS gst_included;

INSERT INTO orders (order_number, customer_id, status, active, subtotal, discount_amount, shipping_fee,
                    total_amount, tax_amount, payment_method, shipping_name, shipping_phone,
                    address_line1, city, state, pincode, created_at, updated_at)
VALUES ('SQL-EXAMPLE-01', @customer, 'PLACED', b'1', @items, 0, @shipping, @items + @shipping,
        ROUND(@items * 18 / 118, 2), 'COD', 'Priya Sharma', '9000000010',
        'Flat 302, Lotus Residency, Kalyani Nagar', 'Pune', 'Maharashtra', '411006', NOW(6), NOW(6));
SET @order := LAST_INSERT_ID();
INSERT INTO order_items (order_id, product_id, seller_id, product_name, store_name, unit_price, quantity,
                         line_total, item_status)
SELECT @order, p.product_id, p.seller_id, p.product_name, u.store_name, p.selling_price, @qty,
       p.selling_price * @qty, 'PLACED'
FROM products p JOIN users u ON u.user_id = p.seller_id WHERE p.product_id = @product;
UPDATE products SET stock_quantity = stock_quantity - @qty WHERE product_id = @product;

-- 8.2 Order dashboard: order ID, customer name, shipping address, total and status
SELECT o.order_id,
       o.order_number,
       u.full_name                                        AS customer_name,
       CONCAT_WS(', ', o.shipping_name, o.address_line1, o.address_line2, o.city,
                 CONCAT(o.state, ' ', o.pincode))         AS shipping_address,
       o.total_amount,
       o.tax_amount                                       AS gst_included,
       CASE WHEN o.status IN ('PLACED','CONFIRMED','PROCESSING','PACKED','CANCELLATION_REQUESTED') THEN 'Pending'
            WHEN o.status IN ('SHIPPED','OUT_FOR_DELIVERY') THEN 'Shipped'
            WHEN o.status = 'DELIVERED' THEN 'Delivered'
            WHEN o.status = 'CANCELLED' THEN 'Cancelled'
            ELSE o.status END                             AS order_status,
       o.active + 0                                       AS active,
       o.created_at,
       o.updated_at
FROM orders o
JOIN users u ON u.user_id = o.customer_id
WHERE o.status <> 'PENDING_PAYMENT'
ORDER BY o.created_at DESC
LIMIT 10 OFFSET 0;

-- 8.3 Filter by status: Pending / Shipped / Delivered / Cancelled
SELECT order_number, status FROM orders WHERE status IN ('PLACED','CONFIRMED','PROCESSING','PACKED');  -- Pending
SELECT order_number, status FROM orders WHERE status IN ('SHIPPED','OUT_FOR_DELIVERY');               -- Shipped
SELECT order_number, status FROM orders WHERE status = 'DELIVERED';                                   -- Delivered
SELECT order_number, status FROM orders WHERE active = b'0';                                          -- Cancelled

-- 8.4 Summary cards
SELECT COUNT(*)                                                                        AS total_orders,
       SUM(status IN ('PLACED','CONFIRMED','PROCESSING','PACKED','CANCELLATION_REQUESTED')) AS pending,
       SUM(status IN ('SHIPPED','OUT_FOR_DELIVERY'))                                   AS shipped,
       SUM(status = 'DELIVERED')                                                       AS delivered,
       SUM(status = 'CANCELLED')                                                       AS cancelled
FROM orders
WHERE status <> 'PENDING_PAYMENT';

-- 8.5 Update order status: Pending -> Shipped (the app also records each step in order_item_events)
UPDATE order_items SET item_status = 'SHIPPED', courier_name = 'ShopSphere Delivery',
                       tracking_number = CONCAT('SSD-', order_item_id), shipped_at = NOW(6)
WHERE order_id = @order AND item_status IN ('PLACED','CONFIRMED','PROCESSING','PACKED');
UPDATE orders SET status = 'SHIPPED', updated_at = NOW(6) WHERE order_id = @order;

-- 8.6 Cancel an order that has NOT been shipped yet (soft delete: the row stays, status = CANCELLED,
--     active = 0, stock goes back). Shipped orders are left untouched.
SET @order2 := (SELECT order_id FROM orders WHERE order_number = 'SQL-EXAMPLE-01');
UPDATE products p JOIN order_items oi ON oi.product_id = p.product_id
SET p.stock_quantity = p.stock_quantity + oi.quantity
WHERE oi.order_id = @order2 AND oi.item_status IN ('PLACED','CONFIRMED','PROCESSING','PACKED');
UPDATE order_items SET item_status = 'CANCELLED'
WHERE order_id = @order2 AND item_status IN ('PLACED','CONFIRMED','PROCESSING','PACKED');
UPDATE orders SET status = 'CANCELLED', active = b'0', status_note = 'Cancelled by ShopSphere', updated_at = NOW(6)
WHERE order_id = @order2 AND status IN ('PLACED','CONFIRMED','PROCESSING','PACKED');
-- (0 rows change here, because the example order was shipped in 8.5.)

-- Clean up the example order created above
UPDATE products p JOIN order_items oi ON oi.product_id = p.product_id
SET p.stock_quantity = p.stock_quantity + oi.quantity
WHERE oi.order_id = @order AND oi.item_status <> 'CANCELLED';
DELETE FROM order_items WHERE order_id = @order;
DELETE FROM orders WHERE order_id = @order;
-- =====================================================================
-- 9. CUSTOMER MANAGEMENT MODULE (assignment)
--    Customers are rows of the users table with role = 'CUSTOMER'.
--    Assignment column  ->  ShopSphere column
--    user_id            ->  user_id (pk)
--    first_name         ->  first_name  (generated by MySQL from full_name)
--    last_name          ->  last_name   (generated by MySQL from full_name)
--    email / phone      ->  email (UNIQUE) / phone
--    status (boolean)   ->  active BIT(1)   (1 = active, 0 = inactive; soft delete)
-- =====================================================================

-- 9.1 Add a new customer (admin). The password is stored only as a BCrypt hash; the app hashes it.
--     Customers can also register themselves from the website (with an email code).
INSERT INTO users (full_name, email, phone, password, role, email_verified, active, created_at, updated_at)
VALUES ('Sneha Kulkarni', 'sneha.sql@example.com', '9812345670',
        '$2a$10$abcdefghijklmnopqrstuuJ7Gd6pY2pXQ6m1kq1bH9mY3u1yXwUe', 'CUSTOMER', b'1', b'1', NOW(6), NOW(6));
SET @customer := LAST_INSERT_ID();

-- 9.2 Customer dashboard: name, email, phone, status and order history totals
SELECT u.user_id,
       u.first_name,
       u.last_name,
       u.email,
       u.phone,
       IF(u.active, 'Active', 'Inactive')                                         AS status,
       COUNT(o.order_id)                                                          AS orders,
       COALESCE(SUM(CASE WHEN o.status IN ('CANCELLED','RETURNED','REFUNDED') THEN 0
                         ELSE o.total_amount END), 0)                             AS total_spent,
       MAX(o.created_at)                                                          AS last_order_at,
       u.created_at
FROM users u
LEFT JOIN orders o ON o.customer_id = u.user_id AND o.status <> 'PENDING_PAYMENT'
WHERE u.role = 'CUSTOMER'
GROUP BY u.user_id
ORDER BY u.created_at DESC
LIMIT 10 OFFSET 0;

-- 9.3 Search by name, email or phone, and filter by status
SELECT user_id, full_name, email, phone, active + 0 AS active
FROM users
WHERE role = 'CUSTOMER'
  AND (LOWER(full_name) LIKE '%sneha%' OR LOWER(email) LIKE '%sneha%' OR phone LIKE '%sneha%')
  AND active = b'1';

-- 9.4 One customer's order history
SELECT order_number, created_at, total_amount, status
FROM orders
WHERE customer_id = (SELECT user_id FROM users WHERE email = 'demo.customer@shopsphere.local')
  AND status <> 'PENDING_PAYMENT'
ORDER BY created_at DESC;

-- 9.5 Summary cards
SELECT COUNT(*)                                              AS total_customers,
       SUM(active = b'1')                                    AS active,
       SUM(active = b'0')                                    AS inactive,
       SUM(created_at >= NOW() - INTERVAL 30 DAY)            AS new_last_30_days
FROM users
WHERE role = 'CUSTOMER';

-- 9.6 Update customer details: contact information and account status
UPDATE users
SET full_name = 'Sneha R. Kulkarni', phone = '9812300000', email = 'sneha.r@example.com', updated_at = NOW(6)
WHERE user_id = @customer AND role = 'CUSTOMER';

-- 9.7 Deactivate (soft delete): the row, addresses and orders stay; the customer cannot sign in
UPDATE users SET active = b'0', updated_at = NOW(6) WHERE user_id = @customer AND role = 'CUSTOMER';

-- 9.8 Reactivate
UPDATE users SET active = b'1', updated_at = NOW(6) WHERE user_id = @customer AND role = 'CUSTOMER';

-- Clean up the example customer created above (only if it has no orders)
DELETE FROM users
WHERE user_id = @customer
  AND user_id NOT IN (SELECT customer_id FROM (SELECT customer_id FROM orders) AS used);
-- =====================================================================
-- 10. PAYMENT MANAGEMENT MODULE (assignment)
--    Assignment column  ->  ShopSphere column
--    payment_id         ->  payment_id (pk)          order_id -> order_id (fk -> orders)
--    amount             ->  amount DECIMAL(12,2)
--    payment_method     ->  method (RAZORPAY | COD) + instrument (CARD, UPI, NETBANKING, WALLET, PAYPAL, CASH)
--    payment_status     ->  status (CREATED, PENDING, SUCCESS = paid, FAILED, REFUNDED, CANCELLED)
--    Refund transactions are logged in the refunds table.
-- =====================================================================

-- 10.1 Payment dashboard: payment ID, order, amount, method and status
SELECT p.payment_id,
       o.order_number,
       u.full_name                                                AS customer,
       p.amount,
       p.method,
       COALESCE(p.instrument, '-')                                AS payment_method,
       p.instrument_detail,
       CASE p.status WHEN 'SUCCESS' THEN 'Paid' ELSE p.status END AS payment_status,
       p.refunded_amount,
       p.razorpay_payment_id,
       p.created_at,
       p.updated_at
FROM payments p
JOIN orders o ON o.order_id = p.order_id
JOIN users u  ON u.user_id  = o.customer_id
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 0;

-- 10.2 Summary cards: collected, refunded, net, cash on delivery still due
SELECT SUM(CASE WHEN status IN ('SUCCESS','REFUNDED') THEN amount ELSE 0 END)                    AS collected,
       SUM(refunded_amount)                                                                     AS refunded,
       SUM(CASE WHEN status IN ('SUCCESS','REFUNDED') THEN amount ELSE 0 END) - SUM(refunded_amount) AS net,
       SUM(status = 'SUCCESS')                                                                  AS successful,
       SUM(status = 'FAILED')                                                                   AS failed,
       SUM(CASE WHEN method = 'COD' AND status = 'PENDING' THEN amount ELSE 0 END)              AS cod_due
FROM payments;

-- 10.3 Payments by method (card, UPI, net banking, wallet, PayPal, cash)
SELECT COALESCE(instrument, 'NOT_COMPLETED') AS payment_method, COUNT(*) AS payments, SUM(amount) AS amount
FROM payments
GROUP BY instrument;

-- 10.4 Payments that can be refunded: paid, money left, order cancelled or returned
SELECT p.payment_id, o.order_number, o.status AS order_status, p.amount, p.refunded_amount,
       p.amount - p.refunded_amount AS refundable
FROM payments p
JOIN orders o ON o.order_id = p.order_id
WHERE p.status = 'SUCCESS'
  AND p.amount > p.refunded_amount
  AND (o.status IN ('CANCELLED','RETURN_REQUESTED','RETURNED','REFUNDED')
       OR EXISTS (SELECT 1 FROM order_items i
                  WHERE i.order_id = o.order_id
                    AND i.item_status IN ('CANCELLED','RETURN_REQUESTED','RETURNED','REFUNDED')));

-- 10.5 Issue a refund (the app calls Razorpay's refund API for online payments first)
SET @payment := (SELECT p.payment_id FROM payments p JOIN orders o ON o.order_id = p.order_id
                 WHERE p.status = 'SUCCESS' AND p.amount > p.refunded_amount
                   AND o.status IN ('CANCELLED','RETURNED','REFUNDED') LIMIT 1);
SET @refund_amount := 10.00;
INSERT INTO refunds (payment_id, amount, mode, status, reason, initiated_by, created_at, updated_at)
SELECT payment_id, @refund_amount, IF(method = 'COD', 'MANUAL', 'RAZORPAY'), 'PROCESSED',
       'SQL example refund', 'ShopSphere Admin', NOW(6), NOW(6)
FROM payments WHERE payment_id = @payment;
UPDATE payments
SET refunded_amount = refunded_amount + @refund_amount,
    status          = IF(refunded_amount + @refund_amount >= amount, 'REFUNDED', status),
    updated_at      = NOW(6)
WHERE payment_id = @payment;

-- 10.6 Refund log (shown on the dashboard)
SELECT r.refund_id, p.payment_id, o.order_number, r.amount, r.mode, r.status, r.reason,
       r.initiated_by, r.razorpay_refund_id, r.created_at
FROM refunds r
JOIN payments p ON p.payment_id = r.payment_id
JOIN orders o   ON o.order_id   = p.order_id
ORDER BY r.created_at DESC;

-- Undo the example refund above (it was only for illustration)
UPDATE payments
SET refunded_amount = refunded_amount - @refund_amount,
    status          = IF(status = 'REFUNDED' AND refunded_amount - @refund_amount < amount, 'SUCCESS', status)
WHERE payment_id = @payment
  AND EXISTS (SELECT 1 FROM refunds WHERE payment_id = @payment AND reason = 'SQL example refund');
DELETE FROM refunds WHERE payment_id = @payment AND reason = 'SQL example refund';
-- =====================================================================
-- 11. CART MANAGEMENT MODULE (assignment)
--    The assignment's single "carts" row (cart_id, customer_id, product_id, quantity, total_price)
--    is split into two tables so a customer has ONE cart holding MANY products:
--      carts      (cart_id, user_id = customer_id, total_price, total_quantity, created_at, updated_at)
--      cart_items (cart_item_id, cart_id, product_id, quantity, line_total, added_at, updated_at)
-- =====================================================================

-- 11.1 A customer's cart: product name, price, quantity and line total
SELECT p.product_name, p.selling_price AS price, ci.quantity, ci.line_total, p.stock_quantity
FROM carts c
JOIN cart_items ci ON ci.cart_id = c.cart_id
JOIN products p    ON p.product_id = ci.product_id
WHERE c.user_id = (SELECT user_id FROM users WHERE email = 'demo.customer@shopsphere.local');

-- 11.2 Add to cart only if enough stock (the app also caps a product at 10 units per order)
SET @cart := (SELECT cart_id FROM carts WHERE user_id = (SELECT user_id FROM users WHERE email = 'demo.customer@shopsphere.local'));
INSERT INTO cart_items (cart_id, product_id, quantity, price_at_add, line_total, added_at, updated_at)
SELECT @cart, product_id, 1, selling_price, selling_price, NOW(6), NOW(6)
FROM products WHERE product_id = 62 AND stock_quantity >= 1 AND active = b'1'
ON DUPLICATE KEY UPDATE quantity = quantity + 1;

-- 11.3 Update quantity (checked against stock) and recalculate the totals
UPDATE cart_items ci JOIN products p ON p.product_id = ci.product_id
SET ci.quantity = 2, ci.line_total = p.selling_price * 2, ci.updated_at = NOW(6)
WHERE ci.cart_id = @cart AND ci.product_id = 62 AND p.stock_quantity >= 2;
UPDATE carts c
SET c.total_price    = (SELECT COALESCE(SUM(line_total), 0) FROM cart_items WHERE cart_id = c.cart_id),
    c.total_quantity = (SELECT COALESCE(SUM(quantity), 0)   FROM cart_items WHERE cart_id = c.cart_id),
    c.updated_at     = NOW(6)
WHERE c.cart_id = @cart;

-- 11.4 Remove from cart and recalculate
DELETE FROM cart_items WHERE cart_id = @cart AND product_id = 62;
UPDATE carts c
SET c.total_price    = (SELECT COALESCE(SUM(line_total), 0) FROM cart_items WHERE cart_id = c.cart_id),
    c.total_quantity = (SELECT COALESCE(SUM(quantity), 0)   FROM cart_items WHERE cart_id = c.cart_id),
    c.updated_at     = NOW(6)
WHERE c.cart_id = @cart;

-- 11.5 Admin: all customer carts with their state (abandoned = items, no activity for 24 hours)
SELECT c.cart_id, u.full_name, u.email, c.total_quantity, c.total_price, c.updated_at AS last_activity,
       CASE WHEN c.total_quantity = 0 THEN 'EMPTY'
            WHEN c.updated_at < NOW() - INTERVAL 24 HOUR THEN 'ABANDONED'
            ELSE 'ACTIVE' END AS state
FROM carts c JOIN users u ON u.user_id = c.user_id
WHERE u.role = 'CUSTOMER'
ORDER BY c.updated_at DESC;

-- 11.6 Cart abandonment: abandoned carts, value left behind, and rate vs orders in the last 30 days
SELECT ab.abandoned_carts, ab.value_left_behind, o.orders_30_days,
       ROUND(ab.abandoned_carts * 100 / NULLIF(ab.abandoned_carts + o.orders_30_days, 0), 1) AS abandonment_rate_pct
FROM (SELECT COUNT(*) AS abandoned_carts, COALESCE(SUM(total_price), 0) AS value_left_behind
      FROM carts WHERE total_quantity > 0 AND updated_at < NOW() - INTERVAL 24 HOUR) ab,
     (SELECT COUNT(*) AS orders_30_days FROM orders
      WHERE status <> 'PENDING_PAYMENT' AND created_at >= NOW() - INTERVAL 30 DAY) o;

-- 11.7 Most abandoned products
SELECT p.product_name, COUNT(*) AS carts, SUM(ci.quantity) AS units, SUM(ci.line_total) AS value
FROM cart_items ci
JOIN carts c    ON c.cart_id = ci.cart_id
JOIN products p ON p.product_id = ci.product_id
WHERE c.updated_at < NOW() - INTERVAL 24 HOUR
GROUP BY p.product_id
ORDER BY carts DESC, value DESC
LIMIT 5;