package com.example.ecommerce.services;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.repositories.PaymentRepository;
import com.razorpay.RazorpayException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Refunds all or part of an order's payment.
 * - Razorpay: calls Razorpay's refund API for exactly the amount (partial refunds add up).
 * - Cash on Delivery not yet collected: nothing to refund; the payment is cancelled when the whole order is.
 * - Cash on Delivery already collected: recorded as a manual refund for the finance team.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    public enum Outcome { NOT_REQUIRED, REFUNDED, MANUAL, FAILED }

    private final PaymentRepository paymentRepository;
    private final RazorpayGateway razorpayGateway;

    @Transactional
    public Outcome refund(CustomerOrder order, BigDecimal requested, boolean wholeOrderCancelled) {
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

        BigDecimal remaining = payment.getAmount().subtract(payment.getRefundedAmount());
        BigDecimal amount = requested.min(remaining);
        if (amount.signum() <= 0) {
            return Outcome.NOT_REQUIRED;
        }

        Outcome outcome;
        if (payment.getMethod() == PaymentMethod.COD) {
            outcome = Outcome.MANUAL;
        } else {
            try {
                razorpayGateway.refund(payment.getRazorpayPaymentId(), PricingPolicy.toPaise(amount));
                outcome = Outcome.REFUNDED;
            } catch (RazorpayException ex) {
                log.error("Refund of {} failed for payment {}: {}", amount, payment.getRazorpayPaymentId(), ex.getMessage());
                payment.setFailureReason("Refund of ₹" + amount + " failed — process it from the Razorpay dashboard");
                return Outcome.FAILED;
            }
        }
        payment.setRefundedAmount(payment.getRefundedAmount().add(amount));
        if (payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }
        return outcome;
    }
}
