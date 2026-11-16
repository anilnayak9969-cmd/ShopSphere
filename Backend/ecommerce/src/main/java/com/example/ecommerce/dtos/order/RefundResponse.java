package com.example.ecommerce.dtos.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.ecommerce.entites.Refund;

/** One refund transaction in the refund log. */
public record RefundResponse(
        Long refundId,
        Long paymentId,
        Long orderId,
        String orderNumber,
        String customerName,
        BigDecimal amount,
        Refund.Mode mode,
        Refund.Status status,
        String razorpayRefundId,
        String reason,
        String initiatedBy,
        LocalDateTime createdAt) {

    public static RefundResponse from(Refund r) {
        return new RefundResponse(r.getRefundId(), r.getPayment().getPaymentId(),
                r.getPayment().getOrder().getOrderId(), r.getPayment().getOrder().getOrderNumber(),
                r.getPayment().getOrder().getCustomer().getFullName(), r.getAmount(), r.getMode(), r.getStatus(),
                r.getRazorpayRefundId(), r.getReason(), r.getInitiatedBy(), r.getCreatedAt());
    }
}
