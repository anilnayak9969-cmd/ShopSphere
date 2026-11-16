package com.example.ecommerce.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.enums.DeliveryZone;
import com.example.ecommerce.enums.ShippingMethod;

/**
 * Shipping Management: works out the shipping cost from the parcel <b>weight</b>, the <b>delivery location</b>
 * (zone) and the <b>shipping method</b>. Pure functions, so the cart, checkout and tests all agree.
 *
 * <pre>
 * zone charge   METRO = base fee · NATIONAL = base + ₹20 · REMOTE = base + ₹50   (base = admin setting, ₹49)
 * weight charge first 0.5 kg included, then ₹15 per extra 0.5 kg (rounded up)
 * STANDARD      zone charge + weight charge; FREE when the order is at least the free-shipping threshold
 *               (₹499), except ₹15 per 0.5 kg above 2 kg for heavy parcels
 * EXPRESS       2 × (zone charge + weight charge), at least ₹99, never free
 * </pre>
 */
public final class ShippingCalculator {

    public static final BigDecimal INCLUDED_WEIGHT_KG = new BigDecimal("0.50");
    public static final BigDecimal FREE_WEIGHT_LIMIT_KG = new BigDecimal("2.00");
    public static final BigDecimal WEIGHT_STEP_KG = new BigDecimal("0.50");
    public static final BigDecimal PER_STEP = new BigDecimal("15.00");
    public static final BigDecimal NATIONAL_SURCHARGE = new BigDecimal("20.00");
    public static final BigDecimal REMOTE_SURCHARGE = new BigDecimal("50.00");
    public static final BigDecimal EXPRESS_MINIMUM = new BigDecimal("99.00");

    private static final Set<String> METRO_CITIES = Set.of("mumbai", "navi mumbai", "thane", "delhi", "new delhi",
            "gurugram", "gurgaon", "noida", "ghaziabad", "bengaluru", "bangalore", "chennai", "kolkata", "hyderabad",
            "secunderabad", "pune", "ahmedabad");

    private static final Set<String> REMOTE_STATES = Set.of("jammu and kashmir", "jammu & kashmir", "ladakh",
            "arunachal pradesh", "assam", "manipur", "meghalaya", "mizoram", "nagaland", "sikkim", "tripura",
            "andaman and nicobar islands", "lakshadweep");

    private ShippingCalculator() {
    }

    /** One priced delivery option. */
    public record Quote(ShippingMethod method, DeliveryZone zone, BigDecimal weightKg, BigDecimal cost, boolean free,
                        int minDays, int maxDays, LocalDate estimatedDelivery) {
    }

    /** Delivery zone from the address: remote PIN ranges/states first, then metro cities, else rest of India. */
    public static DeliveryZone zoneFor(String city, String state, String pincode) {
        String pin = pincode == null ? "" : pincode.trim();
        if (pin.startsWith("18") || pin.startsWith("19") || pin.startsWith("78") || pin.startsWith("79")
                || pin.startsWith("744") || pin.startsWith("6825")
                || (state != null && REMOTE_STATES.contains(state.trim().toLowerCase(Locale.ROOT)))) {
            return DeliveryZone.REMOTE;
        }
        if (city != null && METRO_CITIES.contains(city.trim().toLowerCase(Locale.ROOT))) {
            return DeliveryZone.METRO;
        }
        return DeliveryZone.NATIONAL;
    }

    public static Quote quote(BigDecimal orderValue, BigDecimal weightKg, DeliveryZone zone, ShippingMethod method,
                              PlatformSettings settings, LocalDate today) {
        BigDecimal weight = weightKg == null || weightKg.signum() < 0 ? BigDecimal.ZERO : weightKg.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zoneCharge = settings.getShippingFee().add(switch (zone) {
            case METRO -> BigDecimal.ZERO;
            case NATIONAL -> NATIONAL_SURCHARGE;
            case REMOTE -> REMOTE_SURCHARGE;
        });
        BigDecimal full = zoneCharge.add(stepCharge(weight, INCLUDED_WEIGHT_KG));
        BigDecimal cost;
        boolean free = false;
        if (method == ShippingMethod.EXPRESS) {
            cost = full.multiply(BigDecimal.valueOf(2)).max(EXPRESS_MINIMUM);
        } else if (orderValue != null && orderValue.signum() > 0
                && orderValue.compareTo(settings.getFreeShippingThreshold()) >= 0) {
            cost = stepCharge(weight, FREE_WEIGHT_LIMIT_KG);
            free = cost.signum() == 0;
        } else {
            cost = orderValue == null || orderValue.signum() == 0 ? BigDecimal.ZERO : full;
            free = cost.signum() == 0;
        }
        int[] days = days(zone, method);
        return new Quote(method, zone, weight, cost.setScale(2, RoundingMode.HALF_UP), free, days[0], days[1],
                today.plusDays(days[1]));
    }

    /** Delivery time in days [min, max]. */
    public static int[] days(DeliveryZone zone, ShippingMethod method) {
        boolean express = method == ShippingMethod.EXPRESS;
        return switch (zone) {
            case METRO -> express ? new int[]{1, 2} : new int[]{2, 4};
            case NATIONAL -> express ? new int[]{2, 3} : new int[]{4, 6};
            case REMOTE -> express ? new int[]{3, 5} : new int[]{6, 9};
        };
    }

    /** ₹15 for every started 0.5 kg above the allowance. */
    private static BigDecimal stepCharge(BigDecimal weight, BigDecimal allowance) {
        BigDecimal extra = weight.subtract(allowance);
        if (extra.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal steps = extra.divide(WEIGHT_STEP_KG, 0, RoundingMode.CEILING);
        return PER_STEP.multiply(steps);
    }
}
