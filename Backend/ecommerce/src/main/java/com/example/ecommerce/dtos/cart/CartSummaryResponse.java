package com.example.ecommerce.dtos.cart;

import java.math.BigDecimal;
import java.util.List;

/** Summary cards and abandonment analysis on the admin Cart dashboard. */
public record CartSummaryResponse(
        int abandonedAfterHours,
        long totalCarts,
        long activeCarts,
        long abandonedCarts,
        long emptyCarts,
        BigDecimal activeValue,
        BigDecimal abandonedValue,
        BigDecimal averageAbandonedValue,
        /* Orders placed in the last 30 days. */
        long ordersLast30Days,
        /* abandoned carts ÷ (abandoned carts + orders in the last 30 days) × 100. */
        BigDecimal abandonmentRate,
        List<AbandonedProduct> topAbandonedProducts) {

    /** A product left behind in abandoned carts. */
    public record AbandonedProduct(Long productId, String productName, String imageUrl, long carts, long units,
                                   BigDecimal value) {
    }
}
