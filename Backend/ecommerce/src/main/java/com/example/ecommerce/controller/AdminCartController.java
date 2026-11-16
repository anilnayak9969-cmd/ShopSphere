package com.example.ecommerce.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.cart.AdminCartDetailResponse;
import com.example.ecommerce.dtos.cart.AdminCartResponse;
import com.example.ecommerce.dtos.cart.CartSummaryResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.services.CartAdminService;

import lombok.RequiredArgsConstructor;

/** Cart Management module: customer carts and cart abandonment. ADMIN only (SecurityConfig protects /api/admin/**). */
@RestController
@RequestMapping("/api/admin/carts")
@RequiredArgsConstructor
public class AdminCartController {

    private final CartAdminService cartAdminService;

    /** state=ACTIVE|ABANDONED|EMPTY; abandonedAfterHours = idle time after which a cart counts as abandoned. */
    @GetMapping
    public PageResponse<AdminCartResponse> list(@RequestParam(required = false) String state,
                                                @RequestParam(required = false) String search,
                                                @RequestParam(defaultValue = "" + CartAdminService.DEFAULT_ABANDONED_AFTER_HOURS) int abandonedAfterHours,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return cartAdminService.list(state, search, abandonedAfterHours, page, size);
    }

    @GetMapping("/summary")
    public CartSummaryResponse summary(
            @RequestParam(defaultValue = "" + CartAdminService.DEFAULT_ABANDONED_AFTER_HOURS) int abandonedAfterHours) {
        return cartAdminService.summary(abandonedAfterHours);
    }

    @GetMapping("/{id}")
    public AdminCartDetailResponse get(@PathVariable Long id,
            @RequestParam(defaultValue = "" + CartAdminService.DEFAULT_ABANDONED_AFTER_HOURS) int abandonedAfterHours) {
        return cartAdminService.get(id, abandonedAfterHours);
    }

    /** Sends the customer an in-app reminder about an abandoned cart. */
    @PostMapping("/{id}/remind")
    public AdminCartResponse remind(@PathVariable Long id,
            @RequestParam(defaultValue = "" + CartAdminService.DEFAULT_ABANDONED_AFTER_HOURS) int abandonedAfterHours) {
        return cartAdminService.remind(id, abandonedAfterHours);
    }
}
