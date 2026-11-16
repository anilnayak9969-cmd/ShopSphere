package com.example.ecommerce.dtos.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;

public record OrderResponse(
        Long orderId,
        String orderNumber,
        OrderStatus status,
        String statusNote,
        Set<OrderStatus> nextStatuses,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        BigDecimal refundedAmount,
        BigDecimal subtotal,
        String couponCode,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        /* GST included in the prices (tax-inclusive pricing). */
        BigDecimal taxAmount,
        /* true for active orders, false once cancelled (soft delete). */
        boolean active,
        String customerName,
        String customerEmail,
        /* Full shipping address on one line, for lists. */
        String shippingAddress,
        String shippingName,
        String shippingPhone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        int itemCount,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime deliveredAt,
        List<OrderItemResponse> items,
        /* Shipping Management: method, status, estimated delivery, courier and tracking number. */
        ShipmentInfo shipment) {

    public record ShipmentInfo(com.example.ecommerce.enums.ShippingMethod method,
                               com.example.ecommerce.enums.ShippingStatus status,
                               com.example.ecommerce.enums.DeliveryZone zone, BigDecimal weightKg,
                               java.time.LocalDate estimatedDelivery, String courierService, String trackingNumber) {

        static ShipmentInfo of(com.example.ecommerce.entites.Shipment s) {
            return s == null ? null : new ShipmentInfo(s.getShippingMethod(), s.getShippingStatus(), s.getDeliveryZone(),
                    s.getWeightKg(), s.getEstimatedDelivery(), s.getCourierService(), s.getTrackingNumber());
        }
    }

    /** Payment details that live on the Payment row rather than on the order. */
    public record PaymentInfo(PaymentStatus status, BigDecimal refundedAmount) {
        public static final PaymentInfo NONE = new PaymentInfo(null, BigDecimal.ZERO);
    }

    /** Summary without item details, for list pages. */
    public static OrderResponse summary(CustomerOrder o, PaymentInfo payment) {
        return build(o, payment, List.of());
    }

    public static OrderResponse full(CustomerOrder o, PaymentInfo payment) {
        return build(o, payment, o.getItems().stream().map(OrderItemResponse::from).toList());
    }

    public static OrderResponse full(CustomerOrder o, PaymentInfo payment, List<OrderItemResponse> items) {
        return build(o, payment, items);
    }

    private static OrderResponse build(CustomerOrder o, PaymentInfo payment, List<OrderItemResponse> items) {
        return new OrderResponse(o.getOrderId(), o.getOrderNumber(), o.getStatus(), o.getStatusNote(),
                o.getStatus().nextStatuses(), o.getPaymentMethod(), payment.status(), payment.refundedAmount(),
                o.getSubtotal(), o.getCouponCode(), o.getDiscountAmount(), o.getShippingFee(), o.getTotalAmount(),
                o.getTaxAmount(), o.getStatus() != OrderStatus.CANCELLED,
                o.getCustomer().getFullName(), o.getCustomer().getEmail(), o.getShippingAddress(),
                o.getShippingName(), o.getShippingPhone(),
                o.getAddressLine1(), o.getAddressLine2(), o.getCity(), o.getState(), o.getPincode(),
                o.getItems().stream().mapToInt(i -> i.getQuantity()).sum(), o.getCreatedAt(), o.getPaidAt(),
                o.getDeliveredAt(), items, ShipmentInfo.of(o.getShipment()));
    }
}
