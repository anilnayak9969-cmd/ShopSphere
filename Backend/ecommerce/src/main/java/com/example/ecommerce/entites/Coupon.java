package com.example.ecommerce.entites;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import com.example.ecommerce.enums.CouponStatus;
import com.example.ecommerce.enums.DiscountType;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A promotional code created by an admin. Each customer can redeem a coupon once. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "coupons", uniqueConstraints = @UniqueConstraint(name = "uk_coupons_code", columnNames = "code"))
public class Coupon extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    /** Stored in upper case, e.g. WELCOME10 */
    @Column(nullable = false, length = 30)
    private String code;

    @Column(length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    /** Percent (1-90) for PERCENT, rupees for FLAT. */
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    /** Upper limit for PERCENT coupons; null = no cap. */
    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    /** Total redemptions allowed across all customers; null = unlimited. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    /** Assignment column "status": true = active, false = inactive. */
    @Column(nullable = false)
    private boolean active = true;

    /** Soft delete: the row (and its redemption history) is kept, hidden from the dashboard and unusable. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Optional: the discount applies only to these products. Empty = no product restriction. */
    @ManyToMany
    @JoinTable(name = "coupon_products", joinColumns = @JoinColumn(name = "coupon_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<Product> products = new LinkedHashSet<>();

    /** Optional: the discount applies only to products in these categories (and their subcategories). */
    @ManyToMany
    @JoinTable(name = "coupon_categories", joinColumns = @JoinColumn(name = "coupon_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new LinkedHashSet<>();

    public boolean isRestricted() {
        return !products.isEmpty() || !categories.isEmpty();
    }

    /** The dashboard status at a moment in time. */
    public CouponStatus statusAt(LocalDateTime now) {
        if (deletedAt != null) {
            return CouponStatus.DELETED;
        }
        if (validUntil != null && now.isAfter(validUntil)) {
            return CouponStatus.EXPIRED;
        }
        if (!active) {
            return CouponStatus.INACTIVE;
        }
        if (validFrom != null && now.isBefore(validFrom)) {
            return CouponStatus.SCHEDULED;
        }
        if (usageLimit != null && usedCount >= usageLimit) {
            return CouponStatus.USED_UP;
        }
        return CouponStatus.ACTIVE;
    }
}
