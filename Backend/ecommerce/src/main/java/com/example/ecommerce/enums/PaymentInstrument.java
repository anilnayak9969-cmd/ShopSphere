package com.example.ecommerce.enums;

import java.util.Locale;

/**
 * How the customer actually paid. For online payments Razorpay reports it ("card", "upi", "netbanking",
 * "wallet", ...); PayPal arrives as a wallet named "paypal". Cash on Delivery is CASH.
 */
public enum PaymentInstrument {
    CARD("Credit / debit card"),
    UPI("UPI"),
    NETBANKING("Net banking (bank transfer)"),
    WALLET("Wallet"),
    PAYPAL("PayPal"),
    EMI("Card EMI"),
    PAY_LATER("Pay later"),
    CASH("Cash on delivery"),
    OTHER("Other");

    private final String label;

    PaymentInstrument(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Maps Razorpay's payment "method" (and wallet name) to an instrument. */
    public static PaymentInstrument fromRazorpay(String method, String wallet) {
        if (method == null) {
            return OTHER;
        }
        return switch (method.toLowerCase(Locale.ROOT)) {
            case "card" -> CARD;
            case "upi" -> UPI;
            case "netbanking" -> NETBANKING;
            case "wallet" -> "paypal".equalsIgnoreCase(wallet) ? PAYPAL : WALLET;
            case "emi", "cardless_emi" -> EMI;
            case "paylater" -> PAY_LATER;
            default -> OTHER;
        };
    }
}
