package com.example.ecommerce.dtos.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal total,
        BigDecimal freeShippingThreshold,
        boolean checkoutAllowed,
        boolean hasPriceChanges) {
}
