package com.example.ecommerce.services;

import com.example.ecommerce.dtos.cart.AdminCartDetailResponse;
import com.example.ecommerce.dtos.cart.AdminCartResponse;
import com.example.ecommerce.dtos.cart.CartSummaryResponse;
import com.example.ecommerce.dtos.common.PageResponse;

/** Cart Management module (admin): view customer carts and analyse cart abandonment. */
public interface CartAdminService {

    /** Default number of idle hours after which a cart with items counts as abandoned. */
    int DEFAULT_ABANDONED_AFTER_HOURS = 24;

    /** state: ACTIVE, ABANDONED, EMPTY or null for all. */
    PageResponse<AdminCartResponse> list(String state, String search, int abandonedAfterHours, int page, int size);

    CartSummaryResponse summary(int abandonedAfterHours);

    AdminCartDetailResponse get(Long cartId, int abandonedAfterHours);

    /** Sends the customer an in-app reminder about the items left in an abandoned cart. */
    AdminCartResponse remind(Long cartId, int abandonedAfterHours);
}
