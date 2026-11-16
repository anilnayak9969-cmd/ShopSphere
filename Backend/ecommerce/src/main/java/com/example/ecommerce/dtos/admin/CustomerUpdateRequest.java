package com.example.ecommerce.dtos.admin;

import com.example.ecommerce.dtos.common.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Customer Management: an admin edits a customer's contact details and account status. */
public record CustomerUpdateRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE) String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address")
        @Size(max = 100, message = "Email must be at most 100 characters") String email,
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phone,
        /* true = active, false = inactive (soft delete: the customer cannot sign in, data is kept). */
        @NotNull(message = "Account status is required") Boolean active) {
}
