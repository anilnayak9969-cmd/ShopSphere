package com.example.ecommerce.dtos.delivery;

import com.example.ecommerce.dtos.common.ValidationPatterns;
import com.example.ecommerce.enums.VehicleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Vehicle and service area a delivery partner can update. The vehicle number is checked in the service. */
public record DeliveryPartnerProfileRequest(
        @NotNull(message = "Choose your vehicle") VehicleType vehicleType,
        @Size(max = 15, message = "Vehicle number is too long") String vehicleNumber,
        @NotBlank(message = "City is required") @Size(max = 80) String serviceCity,
        @NotBlank(message = "PIN code is required")
        @Pattern(regexp = ValidationPatterns.PINCODE, message = "Enter a valid 6-digit PIN code") String servicePincode) {
}
