package com.example.ecommerce.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndCouponIdNot(String code, Long couponId);

    Page<Coupon> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Active, currently valid coupons with spare uses — shown as promotions on the home page. */
    @Query("""
            select c from Coupon c
            where c.active = true
              and (c.validFrom is null or c.validFrom <= :now)
              and (c.validUntil is null or c.validUntil >= :now)
              and (c.usageLimit is null or c.usedCount < c.usageLimit)
            order by c.createdAt desc
            """)
    java.util.List<Coupon> currentlyValid(@Param("now") java.time.LocalDateTime now, Pageable pageable);

    /** Atomic "+1" that also respects the usage limit. Returns 0 if the limit was already reached. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Coupon c set c.usedCount = c.usedCount + 1
            where c.couponId = :id and (c.usageLimit is null or c.usedCount < c.usageLimit)
            """)
    int incrementUsage(@Param("id") Long couponId);
}
