package com.example.ecommerce.dtos.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentInstrument;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.services.RefundService;

/** A payment attempt as shown in payment history (customer) or the payment dashboard (admin). */
public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String orderNumber,
        OrderStatus orderStatus,
        String customerName,
        String customerEmail,
        PaymentMethod method,
        /* How the customer paid: CARD, UPI, NETBANKING, WALLET, PAYPAL, CASH … (null if never paid). */
        PaymentInstrument instrument,
        String instrumentLabel,
        String instrumentDetail,
        PaymentStatus status,
        BigDecimal amount,
        BigDecimal refundedAmount,
        /* Amount that can still be refunded. */
        BigDecimal refundable,
        /* True when an admin may issue a refund: paid, money left, and the order was cancelled or returned. */
        boolean refundEligible,
        String razorpayOrderId,
        String razorpayPaymentId,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** Order or item statuses that allow a refund. */
    public static final Set<OrderStatus> REFUNDABLE_STATUSES = EnumSet.of(OrderStatus.CANCELLED,
            OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED, OrderStatus.REFUNDED);

    public static PaymentResponse from(Payment p, boolean includeCustomer) {
        PaymentInstrument instrument = p.getInstrument();
        BigDecimal refundable = RefundService.refundable(p);
        return new PaymentResponse(p.getPaymentId(), p.getOrder().getOrderId(), p.getOrder().getOrderNumber(),
                p.getOrder().getStatus(),
                includeCustomer ? p.getOrder().getCustomer().getFullName() : null,
                includeCustomer ? p.getOrder().getCustomer().getEmail() : null, p.getMethod(),
                instrument, instrument == null ? null : instrument.label(), p.getInstrumentDetail(), p.getStatus(),
                p.getAmount(), p.getRefundedAmount(), refundable, isRefundEligible(p, refundable),
                p.getRazorpayOrderId(), p.getRazorpayPaymentId(),
                p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
    }

    /** Refunds are for cancelled or returned orders, on payments that were actually collected. */
    public static boolean isRefundEligible(Payment p, BigDecimal refundable) {
        if (p.getStatus() != PaymentStatus.SUCCESS || refundable.signum() <= 0) {
            return false;
        }
        return REFUNDABLE_STATUSES.contains(p.getOrder().getStatus())
                || p.getOrder().getItems().stream().anyMatch(i -> REFUNDABLE_STATUSES.contains(i.getItemStatus()));
    }
}
