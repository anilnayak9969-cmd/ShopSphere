package com.example.ecommerce.services;

import java.math.BigDecimal;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.coupon.CouponQuote;
import com.example.ecommerce.dtos.coupon.CouponRequest;
import com.example.ecommerce.dtos.coupon.CouponResponse;
import com.example.ecommerce.entites.Coupon;
import com.example.ecommerce.entites.CustomerOrder;

public interface CouponService {

    /** A coupon that passed every rule, with the discount it gives on a given subtotal. */
    record AppliedCoupon(Coupon coupon, BigDecimal discount) {
    }

    // Admin
    PageResponse<CouponResponse> list(int page, int size);

    CouponResponse create(CouponRequest request);

    CouponResponse update(Long couponId, CouponRequest request);

    CouponResponse setActive(Long couponId, boolean active);

    // Customer
    CouponQuote quote(Long userId, String code);

    /** Validates the code for this customer and subtotal; throws an ApiException explaining why if invalid. */
    AppliedCoupon resolve(Long userId, String code, BigDecimal subtotal);

    /** Called once the order is paid: counts the use and records that this customer used the coupon. */
    void recordRedemption(CustomerOrder order);
}
