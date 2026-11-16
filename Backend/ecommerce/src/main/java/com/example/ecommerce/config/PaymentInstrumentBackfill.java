package com.example.ecommerce.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.enums.PaymentInstrument;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.services.RazorpayGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Payment Management: online payments completed before the payment method was recorded (or while
 * Razorpay could not be reached) get their method filled in from Razorpay, once, in the background.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentInstrumentBackfill {

    private final PaymentRepository paymentRepository;
    private final RazorpayGateway razorpayGateway;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread worker = new Thread(this::run, "payment-instrument-backfill");
        worker.setDaemon(true);
        worker.start();
    }

    void run() {
        int filled = 0;
        for (Payment payment : paymentRepository.findByInstrumentIsNullAndRazorpayPaymentIdIsNotNull()) {
            try {
                RazorpayGateway.PaymentDetails details = razorpayGateway.fetchPaymentDetails(payment.getRazorpayPaymentId());
                payment.setInstrument(PaymentInstrument.fromRazorpay(details.method(), details.wallet()));
                String detail = details.detail();
                payment.setInstrumentDetail(detail == null || detail.length() <= 80 ? detail : detail.substring(0, 80));
                paymentRepository.save(payment);
                filled++;
            } catch (Exception ex) {
                log.warn("Payment method backfill skipped {}: {}", payment.getRazorpayPaymentId(), ex.getMessage());
            }
        }
        if (filled > 0) {
            log.info("Recorded the payment method of {} earlier Razorpay payment(s)", filled);
        }
    }
}
