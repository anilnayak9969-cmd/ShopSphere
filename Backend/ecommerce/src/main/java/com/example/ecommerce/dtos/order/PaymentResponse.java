package com.example.ecommerce.dtos.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;

/** A payment attempt as shown in payment history (customer) or payment monitoring (admin). */
public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String orderNumber,
        String customerName,
        String customerEmail,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        BigDecimal refundedAmount,
        String razorpayOrderId,
        String razorpayPaymentId,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PaymentResponse from(Payment p, boolean includeCustomer) {
        return new PaymentResponse(p.getPaymentId(), p.getOrder().getOrderId(), p.getOrder().getOrderNumber(),
                includeCustomer ? p.getOrder().getCustomer().getFullName() : null,
                includeCustomer ? p.getOrder().getCustomer().getEmail() : null, p.getMethod(), p.getStatus(),
                p.getAmount(), p.getRefundedAmount(), p.getRazorpayOrderId(), p.getRazorpayPaymentId(),
                p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
