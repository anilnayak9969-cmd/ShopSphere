package com.example.ecommerce.enums;

/**
 * What the coupon dashboard shows, worked out from the stored columns (status = active flag,
 * valid_from / valid_until, usage_limit / used_count, deleted_at). Checked in this order.
 */
public enum CouponStatus {
    DELETED("Deleted"),
    EXPIRED("Expired"),
    INACTIVE("Inactive"),
    SCHEDULED("Scheduled"),
    USED_UP("Used up"),
    ACTIVE("Active");

    private final String label;

    CouponStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
