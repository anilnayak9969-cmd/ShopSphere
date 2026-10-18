package com.example.ecommerce.dtos.admin;

import java.math.BigDecimal;

import com.example.ecommerce.entites.PlatformSettings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Admin-editable business rules. Also returned (without commission) to the storefront. */
public record PlatformSettingsDto(
        @NotNull @DecimalMin(value = "0", message = "Commission cannot be negative")
        @DecimalMax(value = "50", message = "Commission cannot exceed 50%") BigDecimal commissionPercent,
        @NotNull @DecimalMin(value = "0", message = "Cannot be negative") BigDecimal freeShippingThreshold,
        @NotNull @DecimalMin(value = "0", message = "Cannot be negative")
        @DecimalMax(value = "1000", message = "Shipping fee is too high") BigDecimal shippingFee,
        @NotNull Boolean codEnabled,
        @NotNull @DecimalMin(value = "1", message = "Must be at least ₹1") BigDecimal codMaxAmount,
        @NotNull @Min(value = 0, message = "Cannot be negative") @Max(value = 60, message = "At most 60 days")
        Integer returnWindowDays,
        @NotNull @Min(value = 0) @Max(value = 1000) Integer lowStockThreshold) {

    public static PlatformSettingsDto from(PlatformSettings s) {
        return new PlatformSettingsDto(s.getCommissionPercent(), s.getFreeShippingThreshold(), s.getShippingFee(),
                s.isCodEnabled(), s.getCodMaxAmount(), s.getReturnWindowDays(), s.getLowStockThreshold());
    }

    /** Public version: the commission rate is internal and not shown to customers. */
    public PlatformSettingsDto withoutCommission() {
        return new PlatformSettingsDto(null, freeShippingThreshold, shippingFee, codEnabled, codMaxAmount,
                returnWindowDays, null);
    }
}
