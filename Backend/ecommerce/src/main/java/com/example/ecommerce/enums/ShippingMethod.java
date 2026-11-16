package com.example.ecommerce.enums;

/** Shipping Management: how fast an order travels. */
public enum ShippingMethod {
    STANDARD("Standard delivery"),
    EXPRESS("Express delivery");

    private final String label;

    ShippingMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
