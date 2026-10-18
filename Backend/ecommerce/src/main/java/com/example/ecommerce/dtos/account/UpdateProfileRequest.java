package com.example.ecommerce.dtos.account;

import com.example.ecommerce.dtos.common.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Only name, phone and store name can change here — never email, role or seller status. */
public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2-100 characters") String fullName,
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phone,
        @Size(max = 120, message = "Store name must be at most 120 characters") String storeName) {
}
