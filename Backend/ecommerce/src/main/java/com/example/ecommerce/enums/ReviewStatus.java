package com.example.ecommerce.enums;

/** Review moderation: new and edited reviews wait for an admin; only approved ones are shown and counted. */
public enum ReviewStatus {
    PENDING("Awaiting approval"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String label;

    ReviewStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
