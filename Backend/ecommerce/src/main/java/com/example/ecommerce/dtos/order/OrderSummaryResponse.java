package com.example.ecommerce.dtos.order;

/** Summary cards on the admin Order dashboard. Unpaid checkout attempts are not counted as orders. */
public record OrderSummaryResponse(
        long total,
        long pending,
        long shipped,
        long delivered,
        long cancelled,
        long returns) {
}
