package com.example.ecommerce.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * The status filters on the admin Order dashboard (Order Management module). Each group bundles the
 * detailed fulfilment statuses that mean the same thing to an administrator.
 */
public enum OrderStatusGroup {
    /** Placed but not shipped yet: still waiting to be confirmed, packed or picked up. */
    PENDING(EnumSet.of(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.PACKED,
            OrderStatus.CANCELLATION_REQUESTED)),
    /** On the way to the customer. */
    SHIPPED(EnumSet.of(OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY)),
    DELIVERED(EnumSet.of(OrderStatus.DELIVERED)),
    /** Soft-deleted orders (orders.active = false). */
    CANCELLED(EnumSet.of(OrderStatus.CANCELLED)),
    RETURNS(EnumSet.of(OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED, OrderStatus.REFUNDED)),
    /** Online checkouts that were started but never paid. */
    UNPAID(EnumSet.of(OrderStatus.PENDING_PAYMENT));

    private final Set<OrderStatus> statuses;

    OrderStatusGroup(Set<OrderStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<OrderStatus> statuses() {
        return statuses;
    }
}
