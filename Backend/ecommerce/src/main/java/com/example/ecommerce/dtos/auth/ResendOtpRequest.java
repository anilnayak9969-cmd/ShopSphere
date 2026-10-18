package com.example.ecommerce.dtos.auth;

import com.example.ecommerce.enums.OtpPurpose;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResendOtpRequest(
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") String email,
        @NotNull(message = "Purpose is required") OtpPurpose purpose) {
}
