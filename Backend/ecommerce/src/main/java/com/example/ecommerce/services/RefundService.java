package com.example.ecommerce.services;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.entites.Refund;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.RefundRepository;
import com.razorpay.RazorpayException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Refunds all or part of an order's payment, and logs every refund transaction in the refunds table.
 * - Razorpay: calls Razorpay's refund API for exactly the amount (partial refunds add up).
 * - Cash on Delivery not yet collected: nothing to refund; the payment is cancelled when the whole order is.
 * - Cash on Delivery already collected: recorded as a manual refund for the finance team.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    public enum Outcome { NOT_REQUIRED, REFUNDED, MANUAL, FAILED }

    /** Name recorded for refunds the system issues on its own (cancellations, approved returns). */
    public static final String SYSTEM = "System";

    private final PaymentRepository paymentRepository;
    private final RazorpayGateway razorpayGateway;
    private final RefundRepository refundRepository;

    /** Automatic refund after a cancellation or an approved return. */
    @Transactional
    public Outcome refund(CustomerOrder order, BigDecimal requested, boolean wholeOrderCancelled) {
        return refund(order, requested, wholeOrderCancelled,
                wholeOrderCancelled ? "Order cancelled" : "Item cancelled or returned", SYSTEM);
    }

    @Transactional
    public Outcome refund(CustomerOrder order, BigDecimal requested, boolean wholeOrderCancelled,
                          String reason, String initiatedBy) {
        Payment payment = paymentRepository.findTopByOrderOrderIdOrderByCreatedAtDesc(order.getOrderId()).orElse(null);
        if (payment == null || requested == null || requested.signum() <= 0) {
            return Outcome.NOT_REQUIRED;
        }
        if (payment.getMethod() == PaymentMethod.COD && payment.getStatus() == PaymentStatus.PENDING) {
            if (wholeOrderCancelled) {
                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setFailureReason("Order cancelled before delivery — nothing was collected");
            }
            return Outcome.NOT_REQUIRED;
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            return Outcome.NOT_REQUIRED;
        }

        BigDecimal amount = requested.min(refundable(payment));
        if (amount.signum() <= 0) {
            return Outcome.NOT_REQUIRED;
        }

        Refund entry = new Refund();
        entry.setPayment(payment);
        entry.setAmount(amount);
        entry.setReason(trim(reason));
        entry.setInitiatedBy(trim(initiatedBy == null ? SYSTEM : initiatedBy));
        Outcome outcome;
        if (payment.getMethod() == PaymentMethod.COD) {
            entry.setMode(Refund.Mode.MANUAL);
            outcome = Outcome.MANUAL;
        } else {
            entry.setMode(Refund.Mode.RAZORPAY);
            try {
                entry.setRazorpayRefundId(razorpayGateway.refund(payment.getRazorpayPaymentId(), PricingPolicy.toPaise(amount)));
                outcome = Outcome.REFUNDED;
            } catch (RazorpayException ex) {
                log.error("Refund of {} failed for payment {}: {}", amount, payment.getRazorpayPaymentId(), ex.getMessage());
                payment.setFailureReason("Refund of ₹" + amount + " failed — process it from the Razorpay dashboard");
                entry.setStatus(Refund.Status.FAILED);
                refundRepository.save(entry);
                return Outcome.FAILED;
            }
        }
        entry.setStatus(Refund.Status.PROCESSED);
        refundRepository.save(entry);
        payment.setRefundedAmount(payment.getRefundedAmount().add(amount));
        if (payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }
        return outcome;
    }

    /** What can still be refunded on a payment. */
    public static BigDecimal refundable(Payment payment) {
        return payment.getAmount().subtract(payment.getRefundedAmount()).max(BigDecimal.ZERO);
    }

    private static String trim(String text) {
        String t = text == null || text.isBlank() ? "Refund" : text.trim();
        return t.length() > 255 ? t.substring(0, 255) : t;
    }
}
