package com.example.ecommerce.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import lombok.Getter;

/**
 * The only class that talks to Razorpay. Keeping it separate makes the checkout logic easy to
 * unit test (the gateway can be mocked) and keeps the secrets in one place.
 */
@Component
public class RazorpayGateway {

    @Getter
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private volatile RazorpayClient client;

    public RazorpayGateway(@Value("${razorpay.key.id}") String keyId,
                           @Value("${razorpay.key.secret}") String keySecret,
                           @Value("${razorpay.webhook.secret:}") String webhookSecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    /** Creates a Razorpay order and returns its id (order_xxx). amount is in paise. */
    public String createOrder(long amountInPaise, String receipt) throws RazorpayException {
        JSONObject request = new JSONObject();
        request.put("amount", amountInPaise);
        request.put("currency", "INR");
        request.put("receipt", receipt);
        com.razorpay.Order order = client().orders.create(request);
        return order.get("id");
    }

    /** Full refund of a captured payment. */
    public void refund(String razorpayPaymentId) throws RazorpayException {
        client().payments.refund(razorpayPaymentId);
    }

    /** Partial (or full) refund of a captured payment, amount in paise. */
    public void refund(String razorpayPaymentId, long amountInPaise) throws RazorpayException {
        JSONObject request = new JSONObject();
        request.put("amount", amountInPaise);
        client().payments.refund(razorpayPaymentId, request);
    }

    /**
     * Checkout callback: Razorpay signs "order_id|payment_id" with the API key secret (HMAC-SHA256).
     * Recomputing it proves the payment really came from Razorpay — the browser cannot forge it.
     */
    public boolean isSignatureValid(String razorpayOrderId, String razorpayPaymentId, String signature) {
        return hmacMatches(razorpayOrderId + "|" + razorpayPaymentId, signature, keySecret);
    }

    public boolean isWebhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    /**
     * Webhooks: Razorpay signs the RAW request body with the separate webhook secret set in the
     * dashboard. The body must be checked exactly as received, before any JSON parsing.
     */
    public boolean isWebhookSignatureValid(String rawBody, String signature) {
        return isWebhookConfigured() && hmacMatches(rawBody, signature, webhookSecret);
    }

    public static boolean isSignatureValid(String orderId, String paymentId, String signature, String secret) {
        return hmacMatches(orderId + "|" + paymentId, signature, secret);
    }

    private static boolean hmacMatches(String payload, String signature, String secret) {
        if (signature == null || payload == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            // Constant-time comparison: does not leak how many characters matched.
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    private RazorpayClient client() throws RazorpayException {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = new RazorpayClient(keyId, keySecret);
                }
            }
        }
        return client;
    }
}
