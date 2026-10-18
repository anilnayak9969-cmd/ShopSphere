package com.example.ecommerce.dtos.auth;

import com.example.ecommerce.dtos.common.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") String email,
        @NotBlank(message = "Code is required")
        @Pattern(regexp = ValidationPatterns.OTP, message = "The code must be 6 digits") String otp,
        @NotBlank(message = "New password is required")
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String newPassword) {
}
