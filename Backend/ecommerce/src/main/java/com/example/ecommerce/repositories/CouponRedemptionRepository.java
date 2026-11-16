package com.example.ecommerce.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.CouponRedemption;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    boolean existsByCouponCouponIdAndUserId(Long couponId, Long userId);

    /** Rows are [couponId, total discount given] for the coupons on a dashboard page; cancelled orders gave nothing. */
    @Query("""
            select r.coupon.couponId, coalesce(sum(r.discountAmount), 0) from CouponRedemption r
            where r.coupon.couponId in :ids and r.order.status <> com.example.ecommerce.enums.OrderStatus.CANCELLED
            group by r.coupon.couponId
            """)
    List<Object[]> discountGiven(@Param("ids") Collection<Long> couponIds);

    /** Row is [orders that used a coupon, total discount given] across all coupons, cancelled orders excluded. */
    @Query("""
            select count(r), coalesce(sum(r.discountAmount), 0) from CouponRedemption r
            where r.order.status <> com.example.ecommerce.enums.OrderStatus.CANCELLED
            """)
    List<Object[]> totals();
}
