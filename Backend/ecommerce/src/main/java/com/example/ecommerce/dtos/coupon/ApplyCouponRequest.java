package com.example.ecommerce.dtos.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyCouponRequest(@NotBlank(message = "Enter a coupon code") @Size(max = 30) String code) {
}
