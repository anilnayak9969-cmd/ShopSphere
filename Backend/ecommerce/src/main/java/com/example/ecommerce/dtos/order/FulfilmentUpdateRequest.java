package com.example.ecommerce.dtos.order;

import com.example.ecommerce.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Seller/admin update of one order line. Courier and tracking number are required when shipping. */
public record FulfilmentUpdateRequest(
        @NotNull(message = "Status is required") OrderStatus status,
        @Size(max = 60, message = "Courier name must be at most 60 characters") String courierName,
        @Size(max = 60, message = "Tracking number must be at most 60 characters")
        @Pattern(regexp = "^$|^[A-Za-z0-9-]+$", message = "Tracking number may contain letters, numbers and - only")
        String trackingNumber,
        @Size(max = 255, message = "Note must be at most 255 characters") String note) {
}
