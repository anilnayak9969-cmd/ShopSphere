package com.example.ecommerce.dtos.order;

import com.example.ecommerce.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderStatusUpdateRequest(
        @NotNull(message = "Status is required") OrderStatus status,
        @Size(max = 255, message = "Note must be at most 255 characters") String note) {
}
