package com.example.ecommerce.dtos.account;

import com.example.ecommerce.dtos.common.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @Size(max = 30, message = "Label must be at most 30 characters") String label,
        @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phone,
        @NotBlank(message = "Address is required") @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank(message = "City is required") @Size(max = 80) String city,
        @NotBlank(message = "State is required") @Size(max = 80) String state,
        @NotBlank(message = "PIN code is required")
        @Pattern(regexp = ValidationPatterns.PINCODE, message = "Enter a valid 6-digit PIN code") String pincode,
        /* Boxed so the field can be omitted (Jackson 3 rejects a missing primitive). null = false. */
        Boolean defaultAddress) {

    public boolean isDefault() {
        return Boolean.TRUE.equals(defaultAddress);
    }
}
