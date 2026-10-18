package com.example.ecommerce.dtos.delivery;

import com.example.ecommerce.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;

/** OUT_FOR_DELIVERY or DELIVERED — the two steps a partner records after picking a parcel up. */
public record DeliveryStatusRequest(@NotNull(message = "Status is required") OrderStatus status) {
}
