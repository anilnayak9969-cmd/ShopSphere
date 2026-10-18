package com.example.ecommerce.dtos.common;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 150) String email,
        @NotBlank(message = "Subject is required") @Size(max = 150, message = "Subject must be at most 150 characters") String subject,
        @NotBlank(message = "Message is required")
        @Size(min = 10, max = 2000, message = "Message must be 10-2000 characters") String message) {
}
