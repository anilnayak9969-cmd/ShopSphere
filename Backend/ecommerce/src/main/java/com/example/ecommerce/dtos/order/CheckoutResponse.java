package com.example.ecommerce.dtos.order;

import com.example.ecommerce.enums.PaymentMethod;

/**
 * For RAZORPAY: everything the React app needs to open Razorpay Checkout (amount in paise).
 * For COD: the order is already placed, so the Razorpay fields are null.
 */
public record CheckoutResponse(
        Long orderId,
        String orderNumber,
        PaymentMethod paymentMethod,
        String razorpayOrderId,
        long amount,
        String currency,
        String keyId,
        String customerName,
        String customerEmail,
        String customerPhone) {
}
