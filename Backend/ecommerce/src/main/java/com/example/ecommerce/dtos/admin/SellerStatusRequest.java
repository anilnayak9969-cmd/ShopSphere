package com.example.ecommerce.dtos.admin;

import com.example.ecommerce.enums.SellerStatus;

import jakarta.validation.constraints.NotNull;

public record SellerStatusRequest(@NotNull(message = "Status is required") SellerStatus status) {
}
