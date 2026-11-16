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
    }
}
