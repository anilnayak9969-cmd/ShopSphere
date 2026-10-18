package com.example.ecommerce.enums;

import java.util.Locale;
import java.util.regex.Pattern;

/** Vehicle a delivery partner uses. A bicycle needs no registration number. */
public enum VehicleType {
    BICYCLE,
    MOTORCYCLE,
    SCOOTER,
    ELECTRIC_SCOOTER;

    /** Indian registration: MH12AB1234 style, or the BH series 22BH1234AA. */
    private static final Pattern NUMBER = Pattern.compile("^([A-Z]{2}\\d{1,2}[A-Z]{0,3}\\d{4}|\\d{2}BH\\d{4}[A-Z]{1,2})$");

    /** Removes spaces and dashes and upper-cases; returns null when the result is not a valid number. */
    public static String normaliseNumber(String raw) {
        String number = raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return NUMBER.matcher(number).matches() ? number : null;
    }

    public boolean needsRegistrationNumber() {
        return this != BICYCLE;
    }
}
