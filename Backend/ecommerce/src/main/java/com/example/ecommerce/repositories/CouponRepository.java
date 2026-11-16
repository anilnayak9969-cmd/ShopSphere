package com.example.ecommerce.repositories;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * Coupon dashboard. status is ALL (every coupon not deleted) or a CouponStatus name; the conditions
     * match Coupon.statusAt (deleted, then expired, inactive, scheduled, used up, active).
     */
    @Query("""
            select c from Coupon c
            where (:search is null
                   or lower(c.code) like lower(concat('%', :search, '%'))
                   or lower(coalesce(c.description, '')) like lower(concat('%', :search, '%')))
              and ((:status = 'DELETED' and c.deletedAt is not null)
                or (:status <> 'DELETED' and c.deletedAt is null and (
                       :status = 'ALL'
                    or (:status = 'EXPIRED' and c.validUntil < :now)
                    or (:status = 'INACTIVE' and c.active = false and (c.validUntil is null or c.validUntil >= :now))
                    or (:status = 'SCHEDULED' and c.active = true and c.validFrom > :now
                        and (c.validUntil is null or c.validUntil >= :now))
                    or (:status = 'USED_UP' and c.active = true and (c.validFrom is null or c.validFrom <= :now)
                        and (c.validUntil is null or c.validUntil >= :now)
                        and c.usageLimit is not null and c.usedCount >= c.usageLimit)
                    or (:status = 'ACTIVE' and c.active = true and (c.validFrom is null or c.validFrom <= :now)
                        and (c.validUntil is null or c.validUntil >= :now)
                        and (c.usageLimit is null or c.usedCount < c.usageLimit)))))
            order by c.createdAt desc
            """)
    Page<Coupon> adminSearch(@Param("status") String status, @Param("search") String search,
                             @Param("now") LocalDateTime now, Pageable pageable);

    /** Active, currently valid coupons with spare uses — shown as promotions on the home page. */
    @Query("""
            select c from Coupon c
            where c.active = true and c.deletedAt is null
              and (c.validFrom is null or c.validFrom <= :now)
              and (c.validUntil is null or c.validUntil >= :now)
              and (c.usageLimit is null or c.usedCount < c.usageLimit)
            order by c.createdAt desc
            """)
    List<Coupon> currentlyValid(@Param("now") LocalDateTime now, Pageable pageable);

    /** Atomic "+1" that also respects the usage limit. Returns 0 if the limit was already reached. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Coupon c set c.usedCount = c.usedCount + 1
            where c.couponId = :id and (c.usageLimit is null or c.usedCount < c.usageLimit)
            """)
    int incrementUsage(@Param("id") Long couponId);
}
