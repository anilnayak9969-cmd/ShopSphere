package com.example.ecommerce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.services.CheckoutService;

import lombok.RequiredArgsConstructor;

/**
 * Razorpay calls this URL directly (no user token), so it is public in SecurityConfig.
 * Trust comes from the X-Razorpay-Signature header, which is checked against the raw body.
 * Configure it in Razorpay Dashboard -> Webhooks with events payment.captured and payment.failed.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final CheckoutService checkoutService;

    /** The body is taken as a String so the signature is checked on exactly the bytes Razorpay sent. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String rawBody,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        checkoutService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
