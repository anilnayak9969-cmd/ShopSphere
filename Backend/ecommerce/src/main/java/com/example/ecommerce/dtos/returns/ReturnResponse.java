package com.example.ecommerce.dtos.returns;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.ReturnRequest;
import com.example.ecommerce.enums.ReturnStatus;

public record ReturnResponse(
        Long returnId,
        Long orderId,
        String orderNumber,
        Long orderItemId,
        Long productId,
        String productName,
        String imageUrl,
        int quantity,
        BigDecimal lineTotal,
        String storeName,
        String customerName,
        String reason,
        String comments,
        ReturnStatus status,
        BigDecimal refundAmount,
        String resolutionNote,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt) {

    public static ReturnResponse from(ReturnRequest r) {
        OrderItem item = r.getOrderItem();
        return new ReturnResponse(r.getReturnId(), item.getOrder().getOrderId(), item.getOrder().getOrderNumber(),
                item.getOrderItemId(), item.getProduct().getProductId(), item.getProductName(), item.getImageUrl(),
                item.getQuantity(), item.getLineTotal(), item.getStoreName(), r.getCustomer().getFullName(),
                r.getReason(), r.getComments(), r.getStatus(), r.getRefundAmount(), r.getResolutionNote(),
                r.getCreatedAt(), r.getResolvedAt());
    }
}
