package com.example.ecommerce.dtos.cart;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** One customer cart on the admin Cart dashboard (Cart Management module). */
public record AdminCartResponse(
        Long cartId,
        Long customerId,
        String customerName,
        String customerEmail,
        boolean customerActive,
        /* ACTIVE (items, recent activity), ABANDONED (items, idle past the threshold) or EMPTY. */
        String state,
        /* Number of different products. */
        int lines,
        /* Units that can be bought right now. */
        int itemCount,
        /* Live total of the buyable items, from current product prices. */
        BigDecimal total,
        /* Products that are out of stock or no longer sold. */
        int unavailableLines,
        List<String> productNames,
        long idleHours,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime reminderSentAt) {
}
