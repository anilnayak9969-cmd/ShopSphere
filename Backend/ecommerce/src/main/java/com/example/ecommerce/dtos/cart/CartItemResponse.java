package com.example.ecommerce.dtos.cart;

import java.math.BigDecimal;

/**
 * available=false means the item stays in the cart but is left out of totals and checkout.
 * priceChanged=true means the price differs from when the customer added it (previousPrice).
 */
public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        String imageUrl,
        String storeName,
        BigDecimal price,
        BigDecimal unitPrice,
        int discountPercent,
        int quantity,
        BigDecimal lineTotal,
        int stockQuantity,
        boolean available,
        String availabilityMessage,
        boolean priceChanged,
        BigDecimal previousPrice) {
}
