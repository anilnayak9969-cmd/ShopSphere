package com.example.ecommerce.dtos.auth;

import com.example.ecommerce.dtos.common.ValidationPatterns;
import com.example.ecommerce.enums.BusinessType;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.VehicleType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign-up for every self-service role. The first block applies to everyone; the seller block and the
 * delivery-partner block are required only for that role (checked in AuthServiceImpl, because which
 * fields are required depends on the chosen role).
 */
public record RegisterRequest(
        // ---- every role
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
        String fullName,
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 150)
        String email,
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone,
        @NotBlank(message = "Password is required")
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password,
        /* CUSTOMER, SELLER or DELIVERY_PARTNER. ADMIN can never be self-assigned (checked in AuthServiceImpl). */
        @NotNull(message = "Choose an account type")
        Role role,
        @NotNull(message = "Please accept the terms and privacy policy")
        Boolean acceptTerms,

        // ---- seller
        @Size(max = 120, message = "Store name must be at most 120 characters")
        String storeName,
        BusinessType businessType,
        @Pattern(regexp = "^$|^\\d{2}[A-Za-z]{5}\\d{4}[A-Za-z][1-9A-Za-z][Zz][0-9A-Za-z]$",
                message = "Enter a valid 15-character GSTIN (e.g. 27ABCDE1234F1Z5)")
        String gstin,
        @Size(max = 255, message = "Pickup address must be at most 255 characters")
        String pickupAddress,
        @Size(max = 80) String pickupCity,
        @Size(max = 80) String pickupState,
        @Pattern(regexp = "^$|" + ValidationPatterns.PINCODE, message = "Enter a valid 6-digit PIN code")
        String pickupPincode,

        // ---- delivery partner
        VehicleType vehicleType,
        @Size(max = 15, message = "Vehicle number is too long")
        String vehicleNumber,
        @Size(max = 80) String serviceCity,
        @Pattern(regexp = "^$|" + ValidationPatterns.PINCODE, message = "Enter a valid 6-digit PIN code")
        String servicePincode) {

    public boolean termsAccepted() {
        return Boolean.TRUE.equals(acceptTerms);
    }
}
