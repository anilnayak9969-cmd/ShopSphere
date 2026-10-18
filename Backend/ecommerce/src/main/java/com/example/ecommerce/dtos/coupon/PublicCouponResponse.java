package com.example.ecommerce.dtos.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.ecommerce.entites.Coupon;
import com.example.ecommerce.enums.DiscountType;

/** What the storefront's promotional banner shows about a live coupon (no usage counts). */
public record PublicCouponResponse(String code, String description, DiscountType discountType, BigDecimal discountValue,
                                   BigDecimal minOrderAmount, BigDecimal maxDiscount, LocalDateTime validUntil) {

    public static PublicCouponResponse from(Coupon c) {
        return new PublicCouponResponse(c.getCode(), c.getDescription(), c.getDiscountType(), c.getDiscountValue(),
                c.getMinOrderAmount(), c.getMaxDiscount(), c.getValidUntil());
    }
}
