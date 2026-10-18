package com.example.ecommerce.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.enums.OrderStatus;

class PricingAndRulesTest {

    @Test
    void sellingPriceAppliesDiscountWithTwoDecimals() {
        Product p = new Product();
        p.applyPricing(new BigDecimal("999.99"), 15);
        assertThat(p.getSellingPrice()).isEqualByComparingTo("849.99");
        assertThat(p.getPrice()).isEqualByComparingTo("999.99");
    }

    @Test
    void shippingIsFreeFromThreshold() {
        PlatformSettings settings = new PlatformSettings(); // defaults: free from 499, else 49
        assertThat(PricingPolicy.shippingFor(new BigDecimal("498.99"), settings)).isEqualByComparingTo("49.00");
        assertThat(PricingPolicy.shippingFor(new BigDecimal("499.00"), settings)).isEqualByComparingTo("0");
        assertThat(PricingPolicy.shippingFor(BigDecimal.ZERO, settings)).isEqualByComparingTo("0");
    }

    @Test
    void rupeesConvertToPaise() {
        assertThat(PricingPolicy.toPaise(new BigDecimal("1499.50"))).isEqualTo(149950L);
        assertThat(PricingPolicy.toPaise(new BigDecimal("49"))).isEqualTo(4900L);
    }

    @Test
    void razorpaySignatureMustMatchSecret() throws Exception {
        String secret = "test_secret";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String valid = HexFormat.of().formatHex(mac.doFinal("order_1|pay_1".getBytes(StandardCharsets.UTF_8)));

        assertThat(RazorpayGateway.isSignatureValid("order_1", "pay_1", valid, secret)).isTrue();
        assertThat(RazorpayGateway.isSignatureValid("order_1", "pay_2", valid, secret)).isFalse();
        assertThat(RazorpayGateway.isSignatureValid("order_1", "pay_1", valid, "other_secret")).isFalse();
        assertThat(RazorpayGateway.isSignatureValid("order_1", "pay_1", null, secret)).isFalse();
    }

    @Test
    void webhookSignatureIsCheckedAgainstRawBodyWithWebhookSecret() throws Exception {
        String body = "{\"event\":\"payment.captured\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("hook_secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        RazorpayGateway gateway = new RazorpayGateway("rzp_test_x", "api_secret", "hook_secret");
        assertThat(gateway.isWebhookSignatureValid(body, signature)).isTrue();
        assertThat(gateway.isWebhookSignatureValid(body + " ", signature)).isFalse();

        RazorpayGateway notConfigured = new RazorpayGateway("rzp_test_x", "api_secret", "");
        assertThat(notConfigured.isWebhookSignatureValid(body, signature)).isFalse();
    }

    @Test
    void orderStatusTransitionsFollowTheLifecycle() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PLACED)).isTrue();
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.PLACED)).isFalse();
        assertThat(OrderStatus.CANCELLED.nextStatuses()).isEmpty();
    }
}
