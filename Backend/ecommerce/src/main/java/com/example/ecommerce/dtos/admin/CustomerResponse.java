package com.example.ecommerce.dtos.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.ecommerce.entites.User;

/** One customer on the admin Customer dashboard, with a summary of their order history. */
public record CustomerResponse(
        Long id,
        String fullName,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean emailVerified,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long orderCount,
        BigDecimal totalSpent,
        LocalDateTime lastOrderAt) {

    /** Order statistics for one customer; EMPTY when they have not ordered yet. */
    public record OrderStats(long orderCount, BigDecimal totalSpent, LocalDateTime lastOrderAt) {
        public static final OrderStats EMPTY = new OrderStats(0, BigDecimal.ZERO, null);
    }

    public static CustomerResponse from(User u, OrderStats stats) {
        String name = u.getFullName() == null ? "" : u.getFullName().trim();
        int space = name.indexOf(' ');
        String first = space < 0 ? name : name.substring(0, space);
        String last = space < 0 ? "" : name.substring(space + 1).trim();
        return new CustomerResponse(u.getId(), u.getFullName(), first, last, u.getEmail(), u.getPhone(),
                u.isEmailVerified(), u.isActive(), u.getCreatedAt(), u.getUpdatedAt(),
                stats.orderCount(), stats.totalSpent(), stats.lastOrderAt());
    }
}
