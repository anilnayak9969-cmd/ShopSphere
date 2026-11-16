package com.example.ecommerce.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Small, idempotent schema fixes that Hibernate's ddl-auto=update cannot make by itself
 * (it adds new columns but never relaxes an existing NOT NULL). Safe to run on every start.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaPatcher implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        // Cash on Delivery payments have no Razorpay order id.
        Integer notNull = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = 'payments'
                  and column_name = 'razorpay_order_id' and is_nullable = 'NO'
                """, Integer.class);
        if (notNull != null && notNull > 0) {
            jdbc.execute("alter table payments modify razorpay_order_id varchar(60) null");
            log.info("Schema patch: payments.razorpay_order_id is now nullable (needed for Cash on Delivery)");
        }

        // Order Management: orders.active (false = cancelled) and orders.tax_amount were added later.
        // Bring rows created before those columns existed into line; both updates are no-ops afterwards.
        int deactivated = jdbc.update("update orders set active = b'0' where status = 'CANCELLED' and active = b'1'");
        int reactivated = jdbc.update("update orders set active = b'1' where status <> 'CANCELLED' and active = b'0'");
        int taxed = jdbc.update("""
                update orders set tax_amount = round((subtotal - discount_amount) * 18 / 118, 2)
                where tax_amount = 0 and subtotal - discount_amount > 0
                """);
        if (deactivated + reactivated + taxed > 0) {
            log.info("Schema patch: synced orders.active on {} row(s), filled orders.tax_amount on {} row(s)",
                    deactivated + reactivated, taxed);
        }

        // Customer Management: users.first_name / users.last_name, worked out by MySQL from full_name
        // ("Priya Sharma" -> "Priya" + "Sharma"). Generated columns are read-only and always in step.
        addGeneratedColumn("users", "first_name",
                "varchar(100) generated always as (substring_index(trim(full_name), ' ', 1)) virtual");
        addGeneratedColumn("users", "last_name",
                "varchar(100) generated always as (trim(substring(trim(full_name), "
                        + "char_length(substring_index(trim(full_name), ' ', 1)) + 1))) virtual");

        // Payment Management: cash on delivery payments are paid in cash; online ones are filled in from
        // Razorpay by PaymentInstrumentBackfill. Refunds made before the refunds log existed get one row each.
        int cash = jdbc.update("update payments set instrument = 'CASH' where method = 'COD' and instrument is null");
        int legacyRefunds = jdbc.update("""
                insert into refunds (payment_id, amount, mode, status, reason, initiated_by, created_at, updated_at)
                select p.payment_id, p.refunded_amount, if(p.method = 'COD', 'MANUAL', 'RAZORPAY'), 'PROCESSED',
                       'Refunded before the refund log was added', 'System', p.updated_at, p.updated_at
                from payments p
                where p.refunded_amount > 0
                  and not exists (select 1 from refunds r where r.payment_id = p.payment_id)
                """);
        if (cash + legacyRefunds > 0) {
            log.info("Schema patch: set {} cash payment(s), logged {} earlier refund(s)", cash, legacyRefunds);
        }

        // Cart Management: fill in cart_items.line_total and carts.total_price / total_quantity for carts that
        // existed before these columns. Only rows still at 0 are touched, and updated_at is left alone.
        int lines = jdbc.update("""
                update cart_items ci join products p on p.product_id = ci.product_id
                set ci.line_total = round(p.selling_price * ci.quantity, 2)
                where ci.line_total = 0
                """);
        int carts = jdbc.update("""
                update carts c
                join (select ci.cart_id, sum(ci.line_total) total, sum(ci.quantity) units
                      from cart_items ci group by ci.cart_id) t on t.cart_id = c.cart_id
                set c.total_price = t.total, c.total_quantity = t.units, c.updated_at = c.updated_at
                where c.total_price = 0 and c.total_quantity = 0
                """);
        if (lines + carts > 0) {
            log.info("Schema patch: filled cart totals ({} item row(s), {} cart(s))", lines, carts);
        }
        patchShipping();
        patchReviews();
    }

    /**
     * Review Management: reviews written before moderation existed were already public, so they are
     * marked approved; the Boolean status column always mirrors moderation_status.
     */
    private void patchReviews() {
        int approved = jdbc.update("""
                update reviews set moderation_status = 'APPROVED', status = b'1', moderated_at = updated_at
                where moderation_status is null
                """);
        int synced = jdbc.update("""
                update reviews set status = if(moderation_status = 'APPROVED', b'1', b'0')
                where status is null or status <> if(moderation_status = 'APPROVED', b'1', b'0')
                """);
        if (approved + synced > 0) {
            log.info("Schema patch: reviews moderation ({} approved existing review(s), {} status synced)", approved, synced);
        }
    }

    /**
     * Shipping Management: realistic product weights (once, while every product still has the 0.5 kg default)
     * and a shipping record for every order placed before the shipping table existed.
     */
    private void patchShipping() {
        Integer custom = jdbc.queryForObject("select count(*) from products where weight_kg <> 0.50", Integer.class);
        if (custom != null && custom == 0) {
            int weighted = jdbc.update("""
                    update products p
                    join categories c on c.category_id = p.category_id
                    left join categories d on d.category_id = c.parent_category_id
                    set p.weight_kg = case
                        when lower(p.product_name) like '%kettlebell%' then 12.50
                        when lower(p.product_name) like '%bicycle%' or lower(p.product_name) like '%bike%' then 14.00
                        when lower(coalesce(d.category_name, c.category_name)) like '%electronic%' then 0.80
                        when lower(coalesce(d.category_name, c.category_name)) like '%home%' then 1.50
                        when lower(coalesce(d.category_name, c.category_name)) like '%sport%' then 1.50
                        when lower(coalesce(d.category_name, c.category_name)) like '%beauty%' then 0.30
                        when lower(coalesce(d.category_name, c.category_name)) like '%book%' then 0.40
                        else 0.50 end
                    """);
            log.info("Schema patch: set shipping weights on {} product(s)", weighted);
        }
        int shipments = jdbc.update("""
                insert into shipping (order_id, courier_service, tracking_number, shipping_status, shipping_cost,
                                      shipping_method, weight_kg, estimated_delivery, shipped_at, delivered_at,
                                      created_at, updated_at)
                select o.order_id,
                       (select i.courier_name from order_items i where i.order_id = o.order_id and i.tracking_number is not null
                        order by i.order_item_id limit 1),
                       (select i.tracking_number from order_items i where i.order_id = o.order_id and i.tracking_number is not null
                        order by i.order_item_id limit 1),
                       case o.status when 'SHIPPED' then 'SHIPPED' when 'OUT_FOR_DELIVERY' then 'OUT_FOR_DELIVERY'
                                     when 'DELIVERED' then 'DELIVERED' when 'RETURN_REQUESTED' then 'DELIVERED'
                                     when 'CANCELLED' then 'CANCELLED' when 'RETURNED' then 'RETURNED'
                                     when 'REFUNDED' then 'RETURNED' else 'PENDING' end,
                       o.shipping_fee, 'STANDARD',
                       coalesce((select sum(i.quantity * p.weight_kg) from order_items i join products p on p.product_id = i.product_id
                                 where i.order_id = o.order_id), 0),
                       date(o.created_at + interval 6 day),
                       (select min(i.shipped_at) from order_items i where i.order_id = o.order_id),
                       o.delivered_at, o.created_at, o.updated_at
                from orders o
                where not exists (select 1 from shipping s where s.order_id = o.order_id)
                """);
        int zoned = 0;
        for (var row : jdbc.queryForList("""
                select s.shipping_id, o.city, o.state, o.pincode from shipping s
                join orders o on o.order_id = s.order_id where s.delivery_zone is null
                """)) {
            zoned += jdbc.update("update shipping set delivery_zone = ? where shipping_id = ?",
                    com.example.ecommerce.services.ShippingCalculator.zoneFor((String) row.get("city"),
                            (String) row.get("state"), (String) row.get("pincode")).name(), row.get("shipping_id"));
        }
        if (shipments + zoned > 0) {
            log.info("Schema patch: created {} shipping record(s), set {} delivery zone(s)", shipments, zoned);
        }
    }

    private void addGeneratedColumn(String table, String column, String definition) {
        Integer exists = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?
                """, Integer.class, table, column);
        if (exists != null && exists == 0) {
            jdbc.execute("alter table " + table + " add column " + column + " " + definition);
            log.info("Schema patch: added generated column {}.{}", table, column);
        }
    }
}
