package com.example.ecommerce.dtos.delivery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.SellerProfile;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentMethod;

/**
 * One parcel as a delivery partner sees it: where to pick it up (the seller's pickup address) and where
 * to drop it. The customer's name, phone and street address are only included once this partner has
 * accepted the job — an open pickup shows just the destination city and PIN code.
 */
public record DeliveryJobResponse(
        Long orderItemId,
        String orderNumber,
        OrderStatus itemStatus,
        Set<OrderStatus> nextStatuses,
        String productName,
        String imageUrl,
        int quantity,
        BigDecimal itemValue,
        PaymentMethod paymentMethod,
        BigDecimal cashToCollect,
        String storeName,
        String pickupAddress,
        String pickupCity,
        String pickupPincode,
        String pickupPhone,
        String dropName,
        String dropPhone,
        String dropAddress,
        String dropCity,
        String dropPincode,
        boolean inYourCity,
        String trackingNumber,
        LocalDateTime orderedAt,
        LocalDateTime assignedAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt) {

    public static DeliveryJobResponse from(OrderItem item, SellerProfile pickup, boolean assignedToViewer, String partnerCity) {
        CustomerOrder o = item.getOrder();
        Set<OrderStatus> next = !assignedToViewer ? Set.of() : switch (item.getItemStatus()) {
            case SHIPPED -> Set.of(OrderStatus.OUT_FOR_DELIVERY);
            case OUT_FOR_DELIVERY -> Set.of(OrderStatus.DELIVERED);
            default -> Set.of();
        };
        String street = String.join(", ", java.util.stream.Stream.of(o.getAddressLine1(), o.getAddressLine2())
                .filter(s -> s != null && !s.isBlank()).toList());
        boolean cod = o.getPaymentMethod() == PaymentMethod.COD;
        return new DeliveryJobResponse(item.getOrderItemId(), o.getOrderNumber(), item.getItemStatus(), next,
                item.getProductName(), item.getImageUrl(), item.getQuantity(), item.getLineTotal(), o.getPaymentMethod(),
                cod ? item.getLineTotal() : BigDecimal.ZERO, item.getStoreName(),
                pickup == null ? null : pickup.getBusinessAddress(), pickup == null ? null : pickup.getCity(),
                pickup == null ? null : pickup.getPincode(),
                pickup == null ? item.getSeller().getPhone() : pickup.getSupportPhone(),
                assignedToViewer ? o.getShippingName() : null, assignedToViewer ? o.getShippingPhone() : null,
                assignedToViewer ? street : null, o.getCity(), o.getPincode(),
                partnerCity != null && partnerCity.equalsIgnoreCase(o.getCity()), item.getTrackingNumber(),
                o.getCreatedAt(), item.getAssignedAt(), item.getShippedAt(), item.getDeliveredAt());
    }
}
