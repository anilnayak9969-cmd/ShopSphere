package com.example.ecommerce.dtos.auth;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;

/** Public view of a user — the password hash is deliberately left out. */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        SellerStatus sellerStatus,
        String storeName,
        boolean emailVerified,
        boolean active,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getRole(),
                user.getSellerStatus(), user.getStoreName(), user.isEmailVerified(), user.isActive(),
                user.getCreatedAt());
    }
}
