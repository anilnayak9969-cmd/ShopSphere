package com.example.ecommerce.dtos.coupon;

import java.math.BigDecimal;

/**
 * The price breakdown the customer will pay if this coupon is used with the current cart.
 * eligibleSubtotal is the part of the cart the discount was worked out on (the whole cart unless the
 * coupon is limited to some products or categories); appliesTo says which.
 */
public record CouponQuote(
        String code,
        String description,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shippingFee,
        BigDecimal total,
        BigDecimal eligibleSubtotal,
        String appliesTo) {
}
