package com.example.ecommerce.dtos.account;

import com.example.ecommerce.dtos.common.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerProfileRequest(
        @NotBlank(message = "Store name is required")
        @Size(min = 2, max = 120, message = "Store name must be 2-120 characters") String storeName,
        @Size(max = 1000, message = "Description must be at most 1000 characters") String description,
        @Size(max = 500)
        @Pattern(regexp = ValidationPatterns.HTTP_URL, message = ValidationPatterns.IMAGE_URL_MESSAGE) String logoUrl,
        @Email(message = "Enter a valid support email") @Size(max = 150) String supportEmail,
        @Pattern(regexp = "^$|" + "^[6-9]\\d{9}$", message = ValidationPatterns.PHONE_MESSAGE) String supportPhone,
        @Pattern(regexp = "^$|^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "Enter a valid 15-character GSTIN (e.g. 27ABCDE1234F1Z5)") String gstin,
        @Size(max = 255) String businessAddress,
        @Size(max = 80) String city,
        @Size(max = 80) String state,
        @Pattern(regexp = "^$|" + "^[1-9]\\d{5}$", message = "Enter a valid 6-digit PIN code") String pincode) {
}
