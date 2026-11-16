package com.example.ecommerce.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.example.ecommerce.entites.PlatformSettings;

/**
 * Money rules shared by the cart, coupons and checkout, so every screen shows the same total.
 * Prices are GST-inclusive, so no separate tax is added; {@link #includedGst} shows how much of the
 * total is tax. Shipping comes from platform settings.
 */
public final class PricingPolicy {

    private PricingPolicy() {
    }

    public static BigDecimal shippingFor(BigDecimal subtotal, PlatformSettings settings) {
        if (subtotal.signum() == 0 || subtotal.compareTo(settings.getFreeShippingThreshold()) >= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return settings.getShippingFee().setScale(2, RoundingMode.HALF_UP);
    }

    /** GST rate assumed for the catalogue. Prices already include it, so it is shown, not added. */
    public static final BigDecimal GST_PERCENT = BigDecimal.valueOf(18);

    /**
     * GST contained in a GST-inclusive amount: amount * rate / (100 + rate).
     * ₹1,180 at 18% contains ₹180 GST.
     */
    public static BigDecimal includedGst(BigDecimal inclusiveAmount) {
        if (inclusiveAmount == null || inclusiveAmount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return inclusiveAmount.multiply(GST_PERCENT)
                .divide(GST_PERCENT.add(BigDecimal.valueOf(100)), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Razorpay expects the smallest currency unit: ₹499.50 -> 49950 paise. */
    public static long toPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * The part of the order discount that belongs to one line, in proportion to its value:
     * refund = lineTotal - discount * lineTotal / subtotal.
     */
    public static BigDecimal refundShare(BigDecimal lineTotal, BigDecimal orderSubtotal, BigDecimal orderDiscount) {
        if (orderDiscount == null || orderDiscount.signum() == 0 || orderSubtotal.signum() == 0) {
            return lineTotal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discountShare = orderDiscount.multiply(lineTotal).divide(orderSubtotal, 2, RoundingMode.HALF_UP);
        return lineTotal.subtract(discountShare).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Platform commission on a gross amount. */
    public static BigDecimal commission(BigDecimal gross, BigDecimal percent) {
        return gross.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
