package com.example.ecommerce.dtos.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentMethod;

/**
 * What a seller sees of an order: only their own line, plus the delivery address they need to
 * dispatch it. No customer email, no other sellers' items, no payment details.
 */
public record SellerOrderItemResponse(
        Long orderItemId,
        String orderNumber,
        OrderStatus orderStatus,
        OrderStatus itemStatus,
        Set<OrderStatus> nextStatuses,
        PaymentMethod paymentMethod,
        LocalDateTime orderedAt,
        Long productId,
        String productName,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String shippingName,
        String shippingPhone,
        String shippingAddress,
        String shippingCity,
        String courierName,
        String trackingNumber,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        String deliveryPartnerName,
        String deliveryPartnerPhone) {

    public static SellerOrderItemResponse from(OrderItem item) {
        CustomerOrder o = item.getOrder();
        String address = String.join(", ", java.util.stream.Stream.of(o.getAddressLine1(), o.getAddressLine2(),
                o.getCity() + ", " + o.getState() + " " + o.getPincode()).filter(s -> s != null && !s.isBlank()).toList());
        // Once a ShopSphere delivery partner has the parcel, they record its progress, not the seller.
        Set<OrderStatus> next = item.getDeliveryPartner() != null ? java.util.EnumSet.noneOf(OrderStatus.class)
                : item.getItemStatus().nextStatuses().stream()
                .filter(OrderStatus.SELLER_TARGETS::contains)
                .collect(java.util.stream.Collectors.toCollection(() -> java.util.EnumSet.noneOf(OrderStatus.class)));
        return new SellerOrderItemResponse(item.getOrderItemId(), o.getOrderNumber(), o.getStatus(),
                item.getItemStatus(), next, o.getPaymentMethod(), o.getCreatedAt(), item.getProduct().getProductId(),
                item.getProductName(), item.getImageUrl(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal(),
                o.getShippingName(), o.getShippingPhone(), address, o.getCity(), item.getCourierName(),
                item.getTrackingNumber(), item.getShippedAt(), item.getDeliveredAt(),
                item.getDeliveryPartner() == null ? null : item.getDeliveryPartner().getFullName(),
                item.getDeliveryPartner() == null ? null : item.getDeliveryPartner().getPhone());
    }
}
