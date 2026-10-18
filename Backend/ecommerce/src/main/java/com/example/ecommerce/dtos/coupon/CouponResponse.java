package com.example.ecommerce.dtos.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.ecommerce.entites.Coupon;
import com.example.ecommerce.enums.DiscountType;

public record CouponResponse(
        Long couponId,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscount,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Integer usageLimit,
        int usedCount,
        boolean active,
        LocalDateTime createdAt) {

    public static CouponResponse from(Coupon c) {
        return new CouponResponse(c.getCouponId(), c.getCode(), c.getDescription(), c.getDiscountType(),
                c.getDiscountValue(), c.getMinOrderAmount(), c.getMaxDiscount(), c.getValidFrom(), c.getValidUntil(),
                c.getUsageLimit(), c.getUsedCount(), c.isActive(), c.getCreatedAt());
    }
}
