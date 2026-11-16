package com.example.ecommerce.dtos.admin;

import java.util.List;

import com.example.ecommerce.dtos.account.AddressResponse;
import com.example.ecommerce.dtos.order.OrderResponse;

/** A customer's profile, saved addresses and latest orders, for the admin customer window. */
public record CustomerDetailResponse(
        CustomerResponse customer,
        List<AddressResponse> addresses,
        List<OrderResponse> recentOrders) {
}
