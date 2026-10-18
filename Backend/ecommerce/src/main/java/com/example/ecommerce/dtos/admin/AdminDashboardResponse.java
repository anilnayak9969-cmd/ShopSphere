package com.example.ecommerce.dtos.admin;

import java.math.BigDecimal;
import java.util.List;

import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.order.OrderResponse;

/** Every number here comes from a live database query — nothing is hard-coded. */
public record AdminDashboardResponse(
        long totalCustomers,
        long totalSellers,
        long pendingSellers,
        long totalCategories,
        long activeCategories,
        long totalProducts,
        long activeProducts,
        long pendingProducts,
        long lowStockProducts,
        long paidOrders,
        long ordersToFulfil,
        long deliveredOrders,
        long cancelledOrders,
        long openReturns,
        long unreadMessages,
        BigDecimal revenue,
        BigDecimal refunded,
        List<OrderResponse> recentOrders,
        List<UserResponse> recentSellerApplications) {
}
