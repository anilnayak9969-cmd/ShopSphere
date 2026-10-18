package com.example.ecommerce.enums;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Status of an order and of each order item.
 *
 * Every order item has its own status because a multi-seller order is fulfilled by each seller
 * independently. The order's status is derived from its items (see {@link #deriveOrderStatus}).
 * {@link #canTransitionTo} is the single place that decides which item status changes are legal.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PLACED,
    CONFIRMED,
    PROCESSING,
    PACKED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLATION_REQUESTED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURNED,
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.ofEntries(
            Map.entry(PENDING_PAYMENT, EnumSet.of(PLACED, CANCELLED)),
            Map.entry(PLACED, EnumSet.of(CONFIRMED, CANCELLED)),
            Map.entry(CONFIRMED, EnumSet.of(PACKED, CANCELLED)),
            Map.entry(PROCESSING, EnumSet.of(PACKED, CANCELLED)),
            Map.entry(PACKED, EnumSet.of(SHIPPED, CANCELLED)),
            Map.entry(SHIPPED, EnumSet.of(OUT_FOR_DELIVERY, DELIVERED)),
            Map.entry(OUT_FOR_DELIVERY, EnumSet.of(DELIVERED)),
            Map.entry(DELIVERED, EnumSet.of(RETURN_REQUESTED)),
            Map.entry(CANCELLATION_REQUESTED, EnumSet.of(CANCELLED, CONFIRMED)),
            Map.entry(CANCELLED, EnumSet.noneOf(OrderStatus.class)),
            Map.entry(RETURN_REQUESTED, EnumSet.of(RETURNED, DELIVERED)),
            Map.entry(RETURNED, EnumSet.of(REFUNDED)),
            Map.entry(REFUNDED, EnumSet.noneOf(OrderStatus.class)));

    /** Forward fulfilment path, used to find the "least advanced" item of an order. */
    private static final List<OrderStatus> PROGRESS =
            List.of(PLACED, CONFIRMED, PROCESSING, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED);

    /** Item statuses a seller or admin may set from the fulfilment screens. */
    public static final Set<OrderStatus> FULFILMENT_TARGETS =
            EnumSet.of(CONFIRMED, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED);

    /**
     * What a seller may do to their own order lines: confirm, pack, or cancel before pickup.
     * Shipping, out-for-delivery and delivered are recorded by the ShopSphere delivery partner who has
     * the parcel (or an admin), because only they can know it reached the customer.
     */
    public static final Set<OrderStatus> SELLER_TARGETS = EnumSet.of(CONFIRMED, PACKED, CANCELLED);

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public Set<OrderStatus> nextStatuses() {
        return ALLOWED.getOrDefault(this, Set.of());
    }

    /** True once an item has left the warehouse — it can no longer be cancelled. */
    public boolean isShippedOrLater() {
        return this == SHIPPED || this == OUT_FOR_DELIVERY || this == DELIVERED || this == RETURN_REQUESTED
                || this == RETURNED || this == REFUNDED;
    }

    /** Items that still count as sold (not cancelled, returned or refunded). */
    public boolean isActiveItem() {
        return this != CANCELLED && this != RETURNED && this != REFUNDED && this != PENDING_PAYMENT;
    }

    /**
     * Order status from item statuses: the least advanced active item decides, so an order is
     * DELIVERED only when every active item is delivered. Fully cancelled/returned orders keep that status.
     */
    public static OrderStatus deriveOrderStatus(List<OrderStatus> itemStatuses) {
        List<OrderStatus> active = itemStatuses.stream().filter(OrderStatus::isActiveItem).toList();
        if (active.isEmpty()) {
            if (itemStatuses.stream().allMatch(s -> s == CANCELLED)) {
                return CANCELLED;
            }
            return itemStatuses.contains(REFUNDED) ? REFUNDED : RETURNED;
        }
        if (active.contains(RETURN_REQUESTED)) {
            return RETURN_REQUESTED;
        }
        return active.stream()
                .min((a, b) -> Integer.compare(PROGRESS.indexOf(a), PROGRESS.indexOf(b)))
                .orElse(PLACED);
    }
}
