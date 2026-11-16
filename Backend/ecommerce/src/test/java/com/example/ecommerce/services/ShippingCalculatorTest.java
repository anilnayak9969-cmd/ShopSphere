package com.example.ecommerce.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.enums.DeliveryZone;
import com.example.ecommerce.enums.ShippingMethod;

/** Shipping Management: cost from weight, delivery location and shipping method (base fee ₹49, free over ₹499). */
class ShippingCalculatorTest {

    private final PlatformSettings settings = new PlatformSettings();
    private final LocalDate today = LocalDate.of(2026, 11, 16);

    private BigDecimal cost(String orderValue, String kg, DeliveryZone zone, ShippingMethod method) {
        return ShippingCalculator.quote(new BigDecimal(orderValue), new BigDecimal(kg), zone, method, settings, today).cost();
    }

    @Test
    void deliveryLocationDecidesTheZone() {
        assertThat(ShippingCalculator.zoneFor("Pune", "Maharashtra", "411006")).isEqualTo(DeliveryZone.METRO);
        assertThat(ShippingCalculator.zoneFor(" bengaluru ", "Karnataka", "560001")).isEqualTo(DeliveryZone.METRO);
        assertThat(ShippingCalculator.zoneFor("Nashik", "Maharashtra", "422001")).isEqualTo(DeliveryZone.NATIONAL);
        assertThat(ShippingCalculator.zoneFor("Guwahati", "Assam", "781001")).isEqualTo(DeliveryZone.REMOTE);
        assertThat(ShippingCalculator.zoneFor("Srinagar", "Jammu and Kashmir", "190001")).isEqualTo(DeliveryZone.REMOTE);
        assertThat(ShippingCalculator.zoneFor("Port Blair", "Andaman and Nicobar Islands", "744101")).isEqualTo(DeliveryZone.REMOTE);
    }

    @Test
    void zoneChangesTheStandardPrice() {
        assertThat(cost("300", "0.40", DeliveryZone.METRO, ShippingMethod.STANDARD)).isEqualByComparingTo("49.00");
        assertThat(cost("300", "0.40", DeliveryZone.NATIONAL, ShippingMethod.STANDARD)).isEqualByComparingTo("69.00");
        assertThat(cost("300", "0.40", DeliveryZone.REMOTE, ShippingMethod.STANDARD)).isEqualByComparingTo("99.00");
    }

    @Test
    void everyStartedHalfKiloAboveTheFirstAddsFifteenRupees() {
        assertThat(cost("300", "0.50", DeliveryZone.METRO, ShippingMethod.STANDARD)).isEqualByComparingTo("49.00");
        assertThat(cost("300", "0.51", DeliveryZone.METRO, ShippingMethod.STANDARD)).isEqualByComparingTo("64.00");
        assertThat(cost("300", "1.50", DeliveryZone.METRO, ShippingMethod.STANDARD)).isEqualByComparingTo("79.00");
        assertThat(cost("300", "12.50", DeliveryZone.NATIONAL, ShippingMethod.STANDARD)).isEqualByComparingTo("429.00");
    }

    @Test
    void standardIsFreeOverTheThresholdUpToTwoKilos() {
        assertThat(cost("499", "2.00", DeliveryZone.REMOTE, ShippingMethod.STANDARD)).isEqualByComparingTo("0");
        assertThat(ShippingCalculator.quote(new BigDecimal("499"), new BigDecimal("2.00"), DeliveryZone.REMOTE,
                ShippingMethod.STANDARD, settings, today).free()).isTrue();
        // Heavy parcels still pay for the weight above 2 kg: 12.5 kg → 21 half-kilos × ₹15.
        assertThat(cost("1799", "12.50", DeliveryZone.METRO, ShippingMethod.STANDARD)).isEqualByComparingTo("315.00");
    }

    @Test
    void expressCostsDoubleWithAMinimumAndIsNeverFree() {
        assertThat(cost("300", "0.40", DeliveryZone.METRO, ShippingMethod.EXPRESS)).isEqualByComparingTo("99.00");
        assertThat(cost("300", "0.40", DeliveryZone.NATIONAL, ShippingMethod.EXPRESS)).isEqualByComparingTo("138.00");
        assertThat(cost("5000", "1.50", DeliveryZone.METRO, ShippingMethod.EXPRESS)).isEqualByComparingTo("158.00");
    }

    @Test
    void methodAndZoneGiveTheDeliveryEstimate() {
        var standard = ShippingCalculator.quote(new BigDecimal("300"), BigDecimal.ONE, DeliveryZone.NATIONAL,
                ShippingMethod.STANDARD, settings, today);
        var express = ShippingCalculator.quote(new BigDecimal("300"), BigDecimal.ONE, DeliveryZone.NATIONAL,
                ShippingMethod.EXPRESS, settings, today);
        assertThat(standard.minDays()).isEqualTo(4);
        assertThat(standard.estimatedDelivery()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(express.estimatedDelivery()).isEqualTo(LocalDate.of(2026, 11, 19));
    }

    @Test
    void baseFeeComesFromAdminSettings() {
        settings.setShippingFee(new BigDecimal("60.00"));
        assertThat(cost("300", "0.40", DeliveryZone.METRO, ShippingMethod.STANDARD)).isEqualByComparingTo("60.00");
    }
}
