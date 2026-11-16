package com.example.ecommerce.enums;

/** Shipping Management: delivery location band used to price shipping and estimate delivery time. */
public enum DeliveryZone {
    /** Metro cities: fastest and cheapest to reach. */
    METRO("Metro city"),
    /** Rest of India. */
    NATIONAL("Rest of India"),
    /** North-East, Jammu & Kashmir, Ladakh and the islands. */
    REMOTE("Remote area");

    private final String label;

    DeliveryZone(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
