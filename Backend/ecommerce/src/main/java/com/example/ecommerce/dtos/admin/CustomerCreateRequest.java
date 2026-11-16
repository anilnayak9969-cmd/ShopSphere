package com.example.ecommerce.dtos.admin;

import com.example.ecommerce.dtos.common.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Customer Management: an admin adds a customer by hand (customers can also register themselves).
 * The admin sets a first password and shares it with the customer, who should change it after signing in.
 */
public record CustomerCreateRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE) String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address")
        @Size(max = 100, message = "Email must be at most 100 characters") String email,
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phone,
        @NotBlank(message = "Password is required")
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String password) {
}
