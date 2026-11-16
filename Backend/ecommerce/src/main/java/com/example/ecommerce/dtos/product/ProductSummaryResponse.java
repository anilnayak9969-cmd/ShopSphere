package com.example.ecommerce.dtos.product;

/** Summary cards at the top of the admin Product dashboard. */
public record ProductSummaryResponse(
        long total,
        long active,
        long inactive,
        long awaitingApproval,
        long lowStock,
        int lowStockThreshold) {
}
