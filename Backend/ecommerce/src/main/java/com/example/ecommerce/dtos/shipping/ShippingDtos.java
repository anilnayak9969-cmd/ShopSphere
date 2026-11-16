package com.example.ecommerce.dtos.shipping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.example.ecommerce.dtos.common.ValidationPatterns;
import com.example.ecommerce.entites.Shipment;
import com.example.ecommerce.enums.DeliveryZone;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.ShippingMethod;
import com.example.ecommerce.enums.ShippingStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response types of the Shipping Management module. */
public final class ShippingDtos {

    private ShippingDtos() {
    }

    /** Price the current cart for an address. */
    public record QuoteRequest(
            @NotBlank(message = "City is required") @Size(max = 80) String city,
            @NotBlank(message = "State is required") @Size(max = 80) String state,
            @NotBlank(message = "PIN code is required")
            @Pattern(regexp = ValidationPatterns.PINCODE, message = "Enter a valid 6-digit PIN code") String pincode,
            @Size(max = 30) String couponCode) {
    }

    public record QuoteOption(ShippingMethod method, String label, BigDecimal cost, boolean free, int minDays,
                              int maxDays, LocalDate estimatedDelivery, BigDecimal total) {
    }

    public record QuoteResponse(DeliveryZone zone, String zoneLabel, BigDecimal weightKg, BigDecimal subtotal,
                                BigDecimal discount, String couponCode, List<QuoteOption> options) {
    }

    /** One row of the admin shipping dashboard. */
    public record ShipmentResponse(Long shippingId, Long orderId, String orderNumber, OrderStatus orderStatus,
                                   String customerName, String customerEmail, String city, String state,
                                   String pincode, DeliveryZone deliveryZone, ShippingMethod shippingMethod,
                                   BigDecimal weightKg, BigDecimal shippingCost, String courierService,
                                   String trackingNumber, ShippingStatus shippingStatus, LocalDate estimatedDelivery,
                                   LocalDateTime shippedAt, LocalDateTime deliveredAt, LocalDateTime createdAt,
                                   LocalDateTime updatedAt, int itemCount) {

        public static ShipmentResponse from(Shipment s) {
            var o = s.getOrder();
            return new ShipmentResponse(s.getShippingId(), o.getOrderId(), o.getOrderNumber(), o.getStatus(),
                    o.getCustomer().getFullName(), o.getCustomer().getEmail(), o.getCity(), o.getState(),
                    o.getPincode(), s.getDeliveryZone(), s.getShippingMethod(), s.getWeightKg(), s.getShippingCost(),
                    s.getCourierService(), s.getTrackingNumber(), s.getShippingStatus(), s.getEstimatedDelivery(),
                    s.getShippedAt(), s.getDeliveredAt(), s.getCreatedAt(), s.getUpdatedAt(),
                    o.getItems().stream().mapToInt(i -> i.getQuantity()).sum());
        }
    }

    public record SummaryResponse(long pending, long shipped, long inTransit, long outForDelivery, long delivered,
                                  long cancelledOrReturned, long express, BigDecimal shippingRevenue) {
    }

    /** Admin edits the courier, tracking number and status of a shipped order. */
    public record UpdateRequest(
            @NotBlank(message = "Courier service is required")
            @Size(min = 2, max = 100, message = "Courier service must be 2-100 characters") String courierService,
            @NotBlank(message = "Tracking number is required")
            @Pattern(regexp = "^[A-Za-z0-9-]{5,40}$", message = "Tracking number must be 5-40 letters, numbers or -")
            String trackingNumber,
            /* SHIPPED, IN_TRANSIT, OUT_FOR_DELIVERY or DELIVERED; null keeps the current status. */
            ShippingStatus status,
            @Size(max = 255) String note) {
    }

    public record TrackedItem(String productName, String imageUrl, int quantity, OrderStatus itemStatus) {
    }

    public record TrackingLine(String productName, OrderStatus status, String description, LocalDateTime at) {
    }

    /** What a customer sees when tracking a shipment. */
    public record TrackingResponse(String trackingNumber, String courierService, ShippingStatus shippingStatus,
                                   ShippingMethod shippingMethod, Long orderId, String orderNumber, String city,
                                   String pincode, LocalDate estimatedDelivery, LocalDateTime shippedAt,
                                   LocalDateTime deliveredAt, List<TrackedItem> items, List<TrackingLine> history) {
    }
}
