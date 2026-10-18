package com.example.ecommerce.dtos.admin;

import java.math.BigDecimal;
import java.util.List;

import com.example.ecommerce.dtos.order.SellerOrderItemResponse;
import com.example.ecommerce.enums.SellerStatus;

public record SellerDashboardResponse(
        SellerStatus sellerStatus,
        String storeName,
        long totalProducts,
        long activeProducts,
        long pendingApprovalProducts,
        long lowStockProducts,
        int lowStockThreshold,
        long itemsToFulfil,
        long itemsDelivered,
        long openReturns,
        long orders,
        long unitsSold,
        BigDecimal revenue,
        List<SellerOrderItemResponse> recentOrderItems) {
}
