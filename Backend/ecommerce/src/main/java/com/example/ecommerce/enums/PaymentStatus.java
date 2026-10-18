package com.example.ecommerce.enums;

public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED,
    /** A Cash on Delivery order was cancelled before any money was collected. */
    CANCELLED
}
