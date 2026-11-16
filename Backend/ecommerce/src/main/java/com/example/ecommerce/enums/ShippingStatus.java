package com.example.ecommerce.enums;

/** Shipping Management: where the shipment of an order is. */
public enum ShippingStatus {
    /** Order placed, not handed to the courier yet. */
    PENDING,
    SHIPPED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    RETURNED;

    /** Shipping status that follows from the order status (IN_TRANSIT is set by an admin while SHIPPED). */
    public static ShippingStatus fromOrder(OrderStatus status, ShippingStatus current) {
        return switch (status) {
            case SHIPPED -> current == IN_TRANSIT ? IN_TRANSIT : SHIPPED;
            case OUT_FOR_DELIVERY -> OUT_FOR_DELIVERY;
            case DELIVERED, RETURN_REQUESTED -> DELIVERED;
            case CANCELLED -> CANCELLED;
            case RETURNED, REFUNDED -> RETURNED;
            default -> PENDING;
        };
    }

    public boolean isShipped() {
        return this == SHIPPED || this == IN_TRANSIT || this == OUT_FOR_DELIVERY || this == DELIVERED;
    }
}
