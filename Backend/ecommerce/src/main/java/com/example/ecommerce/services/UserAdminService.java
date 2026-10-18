package com.example.ecommerce.services;

import com.example.ecommerce.dtos.admin.CreateAdminRequest;
import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;

public interface UserAdminService {

    PageResponse<UserResponse> listUsers(Role role, SellerStatus sellerStatus, String search, int page, int size);

    UserResponse updateSellerStatus(Long sellerId, SellerStatus status);

    UserResponse setUserActive(Long adminId, Long userId, boolean active);

    /** An admin registers another admin account (verified and active immediately). */
    UserResponse createAdmin(Long creatorId, CreateAdminRequest request);
}
