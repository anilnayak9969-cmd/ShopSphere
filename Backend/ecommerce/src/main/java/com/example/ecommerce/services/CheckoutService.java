package com.example.ecommerce.services;

import com.example.ecommerce.dtos.order.CheckoutRequest;
import com.example.ecommerce.dtos.order.CheckoutResponse;
import com.example.ecommerce.dtos.order.OrderResponse;
import com.example.ecommerce.dtos.order.PaymentFailedRequest;
import com.example.ecommerce.dtos.order.VerifyPaymentRequest;

/**
 * Razorpay flow:
 * 1. createCheckout   — server prices the cart (and coupon), saves a PENDING_PAYMENT order, creates a Razorpay order.
 * 2. (browser)        — Razorpay Checkout collects the payment.
 * 3. verifyPayment    — server checks Razorpay's signature, reserves stock, marks the order PLACED.
 * 4. handleWebhook    — Razorpay's server-to-server confirmation; completes the order even if the
 *                       customer closed the browser before step 3.
 */
public interface CheckoutService {

    CheckoutResponse createCheckout(Long userId, CheckoutRequest request);

    OrderResponse verifyPayment(Long userId, VerifyPaymentRequest request);

    void recordPaymentFailure(Long userId, PaymentFailedRequest request);

    void handleWebhook(String rawBody, String signature);
}
