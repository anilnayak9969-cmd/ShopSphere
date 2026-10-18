package com.example.ecommerce.dtos.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.ecommerce.enums.DiscountType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CouponRequest(
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^[A-Za-z0-9]{4,30}$", message = "Code must be 4-30 letters or numbers") String code,
        @Size(max = 200, message = "Description must be at most 200 characters") String description,
        @NotNull(message = "Choose a discount type") DiscountType discountType,
        @NotNull(message = "Discount value is required")
        @DecimalMin(value = "1", message = "Discount must be at least 1")
        @DecimalMax(value = "100000", message = "Discount is too large")
        @Digits(integer = 8, fraction = 2) BigDecimal discountValue,
        @DecimalMin(value = "0", message = "Minimum order cannot be negative")
        @Digits(integer = 8, fraction = 2) BigDecimal minOrderAmount,
        @DecimalMin(value = "1", message = "Maximum discount must be at least 1")
        @Digits(integer = 8, fraction = 2) BigDecimal maxDiscount,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        @Min(value = 1, message = "Usage limit must be at least 1") Integer usageLimit,
        /* Boxed so it can be omitted; a new coupon is active unless explicitly false. */
        Boolean active) {

    public boolean isActive() {
        return !Boolean.FALSE.equals(active);
    }
}
