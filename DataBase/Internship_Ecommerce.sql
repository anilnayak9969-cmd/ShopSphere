SELECT * FROM ecommerce_intern.users;

USE ecommerce_intern;

-- 1) Look before deleting: who is user 13, and do they have orders?
SELECT user_id, full_name, email, role, store_name FROM users WHERE user_id = 13;

SELECT (SELECT COUNT(*) FROM orders      WHERE customer_id = 13)
     + (SELECT COUNT(*) FROM order_items WHERE seller_id = 13 OR delivery_partner_id = 13) AS linked_orders;
-- If linked_orders > 0, do NOT delete. Suspend instead:
-- UPDATE users SET active = b'0' WHERE user_id = 13;

-- 2) Delete user 13 and everything that points to it (child rows first, then the user)
SET SQL_SAFE_UPDATES = 0;   -- Workbench blocks some multi-row deletes without this
START TRANSACTION;

SET @uid   := 13;
SET @email := (SELECT email FROM users WHERE user_id = @uid);

-- the seller's products and their details
DELETE FROM product_specifications WHERE product_id IN (SELECT product_id FROM (SELECT product_id FROM products WHERE seller_id = @uid) p);
DELETE FROM product_images         WHERE product_id IN (SELECT product_id FROM (SELECT product_id FROM products WHERE seller_id = @uid) p);
DELETE FROM cart_items             WHERE product_id IN (SELECT product_id FROM (SELECT product_id FROM products WHERE seller_id = @uid) p);
DELETE FROM wishlist_items         WHERE product_id IN (SELECT product_id FROM (SELECT product_id FROM products WHERE seller_id = @uid) p);
DELETE FROM products               WHERE seller_id = @uid;

-- the user's own rows
DELETE FROM seller_profiles           WHERE user_id = @uid;
DELETE FROM delivery_partner_profiles WHERE user_id = @uid;
DELETE ci FROM cart_items ci JOIN carts c ON c.cart_id = ci.cart_id WHERE c.user_id = @uid;
DELETE FROM carts                     WHERE user_id = @uid;
DELETE FROM wishlist_items            WHERE user_id = @uid;
DELETE FROM addresses                 WHERE user_id = @uid;
DELETE FROM reviews                   WHERE user_id = @uid;
DELETE FROM notifications             WHERE user_id = @uid;
DELETE FROM email_otps                WHERE email = @email;

-- finally the user
DELETE FROM users WHERE user_id = @uid;

COMMIT;
SET SQL_SAFE_UPDATES = 1;

-- 3) Confirm
SELECT COUNT(*) AS user_13_left FROM users WHERE user_id = 13;   -- expect 0
SELECT user_id, full_name, email, role FROM users ORDER BY user_id;

