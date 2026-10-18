package com.example.ecommerce.dtos.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentFailedRequest(@NotBlank @Size(max = 60) String razorpayOrderId, @Size(max = 500) String reason) {
}
