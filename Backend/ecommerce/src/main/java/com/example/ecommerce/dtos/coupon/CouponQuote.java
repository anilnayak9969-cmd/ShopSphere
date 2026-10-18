package com.example.ecommerce.dtos.coupon;

import java.math.BigDecimal;

/** The price breakdown the customer will pay if this coupon is used with the current cart. */
public record CouponQuote(
        String code,
        String description,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shippingFee,
        BigDecimal total) {
}
