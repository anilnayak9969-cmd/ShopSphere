package com.example.ecommerce.dtos.order;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payment Management: an admin issues a refund for a cancelled or returned order. */
public record RefundRequest(
        @NotNull(message = "Refund amount is required")
        @DecimalMin(value = "1.00", message = "Refund amount must be at least ₹1")
        @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places")
        BigDecimal amount,
        @NotBlank(message = "Give a reason for the refund")
        @Size(min = 5, max = 255, message = "Reason must be 5-255 characters")
        String reason) {
}
