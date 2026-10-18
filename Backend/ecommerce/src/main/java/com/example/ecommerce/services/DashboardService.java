package com.example.ecommerce.services;

import com.example.ecommerce.dtos.admin.AdminDashboardResponse;
import com.example.ecommerce.dtos.admin.SellerDashboardResponse;

public interface DashboardService {

    AdminDashboardResponse adminDashboard();

    SellerDashboardResponse sellerDashboard(Long sellerId);
}
