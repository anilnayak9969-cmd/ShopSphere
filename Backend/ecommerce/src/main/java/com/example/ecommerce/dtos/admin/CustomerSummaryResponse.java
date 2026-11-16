package com.example.ecommerce.dtos.admin;

/** Summary cards on the admin Customer dashboard. */
public record CustomerSummaryResponse(
        long total,
        long active,
        long inactive,
        long newLast30Days,
        long withOrders) {
}
