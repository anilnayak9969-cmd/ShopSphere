package com.example.ecommerce.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.example.ecommerce.dtos.cart.CartResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.coupon.CouponQuote;
import com.example.ecommerce.dtos.coupon.CouponRequest;
import com.example.ecommerce.dtos.coupon.CouponResponse;
import com.example.ecommerce.entites.Coupon;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.enums.CouponStatus;

/** Coupon and Discount Management. */
public interface CouponService {

    /** One line of the cart or order the coupon is checked against. productId is null for "the whole order". */
    record CouponLine(Long productId, BigDecimal lineTotal) {
    }

    /**
     * A coupon that passed every rule, with the discount it gives. eligibleProductIds are the products the
     * discount was worked out on (empty when the coupon applies to the whole order).
     */
    record AppliedCoupon(Coupon coupon, BigDecimal discount, BigDecimal eligibleSubtotal, Set<Long> eligibleProductIds) {

        public AppliedCoupon(Coupon coupon, BigDecimal discount) {
            this(coupon, discount, discount, Set.of());
        }

        public boolean restricted() {
            return coupon.isRestricted();
        }
    }

    /** Cards on the coupon dashboard. */
    record CouponSummary(long active, long scheduled, long expired, long inactive, long usedUp, long deleted,
                         long redemptions, BigDecimal discountGiven) {
    }

    // Admin
    /** status null = every coupon that is not deleted. */
    PageResponse<CouponResponse> list(CouponStatus status, String search, int page, int size);

    CouponSummary summary();

    CouponResponse get(Long couponId);

    CouponResponse create(CouponRequest request);

    CouponResponse update(Long couponId, CouponRequest request);

    CouponResponse setActive(Long couponId, boolean active);

    /** Soft delete: the coupon stops working and leaves the dashboard; the row and its history are kept. */
    CouponResponse delete(Long couponId);

    /** Brings a deleted coupon back as inactive, so the admin can review it before activating. */
    CouponResponse restore(Long couponId);

    // Customer
    /** Up to three live coupons for the storefront's promotional banner. */
    List<com.example.ecommerce.dtos.coupon.PublicCouponResponse> publicCoupons();

    CouponQuote quote(Long userId, String code);

    /** Validates the code for this customer and these lines; throws an ApiException explaining why if invalid. */
    AppliedCoupon resolve(Long userId, String code, List<CouponLine> lines);

    /** Whole-order check (no item details): only coupons without product or category limits can apply. */
    default AppliedCoupon resolve(Long userId, String code, BigDecimal subtotal) {
        return resolve(userId, code, List.of(new CouponLine(null, subtotal)));
    }

    /** Called once the order is paid: counts the use and records that this customer used the coupon. */
    void recordRedemption(CustomerOrder order);

    /** The cart's available lines, for {@link #resolve(Long, String, List)}. */
    static List<CouponLine> linesOf(CartResponse cart) {
        return cart.items().stream().filter(i -> i.available())
                .map(i -> new CouponLine(i.productId(), i.lineTotal())).toList();
    }
}
