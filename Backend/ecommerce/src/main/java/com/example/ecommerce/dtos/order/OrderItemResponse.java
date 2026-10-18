package com.example.ecommerce.dtos.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.ReturnRequest;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.ReturnStatus;

/**
 * One line of an order as the customer or admin sees it, including its own fulfilment status,
 * courier tracking and — when relevant — the return request and whether a return is still allowed.
 */
public record OrderItemResponse(
        Long orderItemId,
        Long productId,
        String productName,
        String storeName,
        String imageUrl,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        OrderStatus itemStatus,
        String courierName,
        String trackingNumber,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        ReturnStatus returnStatus,
        boolean returnable,
        LocalDateTime returnableUntil,
        String deliveryPartnerName,
        List<TrackingEvent> tracking) {

    public static OrderItemResponse from(OrderItem item) {
        return from(item, null, 0, List.of());
    }

    /** @param returnWindowDays days after delivery during which a return may be requested (0 = not shown) */
    public static OrderItemResponse from(OrderItem item, ReturnRequest returnRequest, int returnWindowDays,
                                         List<TrackingEvent> tracking) {
        LocalDateTime until = item.getDeliveredAt() == null || returnWindowDays <= 0
                ? null : item.getDeliveredAt().plusDays(returnWindowDays);
        boolean returnable = returnRequest == null && item.getItemStatus() == OrderStatus.DELIVERED
                && until != null && LocalDateTime.now().isBefore(until);
        return new OrderItemResponse(item.getOrderItemId(), item.getProduct().getProductId(), item.getProductName(),
                item.getStoreName(), item.getImageUrl(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal(),
                item.getItemStatus(), item.getCourierName(), item.getTrackingNumber(), item.getShippedAt(),
                item.getDeliveredAt(), returnRequest == null ? null : returnRequest.getStatus(), returnable,
                returnable ? until : null,
                item.getDeliveryPartner() == null ? null : item.getDeliveryPartner().getFullName().split(" ")[0],
                tracking);
    }
}
