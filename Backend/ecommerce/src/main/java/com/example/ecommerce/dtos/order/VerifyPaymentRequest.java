package com.example.ecommerce.dtos.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The three values Razorpay Checkout hands back to the browser after a successful payment. */
public record VerifyPaymentRequest(
        @NotBlank @Size(max = 60) String razorpayOrderId,
        @NotBlank @Size(max = 60) String razorpayPaymentId,
        @NotBlank @Size(max = 128) String razorpaySignature) {
}
