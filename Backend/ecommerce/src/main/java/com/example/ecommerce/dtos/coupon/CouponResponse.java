package com.example.ecommerce.dtos.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.example.ecommerce.entites.Coupon;
import com.example.ecommerce.enums.CouponStatus;
import com.example.ecommerce.enums.DiscountType;

/** A coupon on the admin dashboard, with its computed status, limits and how much it has been used. */
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        CouponStatus status,
        String statusLabel,
        List<ProductRef> products,
        List<CategoryRef> categories,
        String appliesTo,
        BigDecimal discountGiven) {

    public record ProductRef(Long productId, String productName) {
    }

    public record CategoryRef(Integer categoryId, String categoryName) {
    }

    public static CouponResponse from(Coupon c) {
        return from(c, BigDecimal.ZERO);
    }

    public static CouponResponse from(Coupon c, BigDecimal discountGiven) {
        CouponStatus status = c.statusAt(LocalDateTime.now());
        List<ProductRef> products = c.getProducts().stream()
                .map(p -> new ProductRef(p.getProductId(), p.getProductName()))
                .sorted(Comparator.comparing(ProductRef::productName)).toList();
        List<CategoryRef> categories = c.getCategories().stream()
                .map(cat -> new CategoryRef(cat.getCategoryId(), cat.getCategoryName()))
                .sorted(Comparator.comparing(CategoryRef::categoryName)).toList();
        return new CouponResponse(c.getCouponId(), c.getCode(), c.getDescription(), c.getDiscountType(),
                c.getDiscountValue(), c.getMinOrderAmount(), c.getMaxDiscount(), c.getValidFrom(), c.getValidUntil(),
                c.getUsageLimit(), c.getUsedCount(), c.isActive(), c.getCreatedAt(), c.getUpdatedAt(), c.getDeletedAt(),
                status, status.label(), products, categories, appliesTo(c), discountGiven);
    }

    /** "All products", "Electronics, Books" or "2 products · Electronics". */
    public static String appliesTo(Coupon c) {
        if (!c.isRestricted()) {
            return "All products";
        }
        List<String> parts = new java.util.ArrayList<>();
        if (!c.getCategories().isEmpty()) {
            parts.add(String.join(", ", c.getCategories().stream().map(cat -> cat.getCategoryName()).sorted().toList()));
        }
        if (!c.getProducts().isEmpty()) {
            parts.add(c.getProducts().size() == 1 ? c.getProducts().iterator().next().getProductName()
                    : c.getProducts().size() + " selected products");
        }
        return String.join(" · ", parts);
    }
}
