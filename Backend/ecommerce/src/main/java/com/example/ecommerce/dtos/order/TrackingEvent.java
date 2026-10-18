package com.example.ecommerce.dtos.order;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.OrderItemEvent;
import com.example.ecommerce.enums.OrderStatus;

/** One line of an item's tracking history, oldest first. */
public record TrackingEvent(OrderStatus status, String description, LocalDateTime at) {

    public static TrackingEvent from(OrderItemEvent e) {
        return new TrackingEvent(e.getStatus(), e.getDescription(), e.getCreatedAt());
    }
}
