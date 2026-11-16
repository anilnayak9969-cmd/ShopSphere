package com.example.ecommerce.dtos.cart;

/** A customer's cart as the admin sees it: the summary row plus the full cart (items, prices, totals). */
public record AdminCartDetailResponse(
        AdminCartResponse summary,
        CartResponse cart) {
}
