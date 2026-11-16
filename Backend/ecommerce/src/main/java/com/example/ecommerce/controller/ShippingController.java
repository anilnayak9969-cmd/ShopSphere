package com.example.ecommerce.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.QuoteRequest;
import com.example.ecommerce.dtos.shipping.ShippingDtos.QuoteResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.ShipmentResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.SummaryResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.TrackingResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.UpdateRequest;
import com.example.ecommerce.enums.ShippingStatus;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.ShippingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Shipping Management module. /api/customer/** is CUSTOMER only and /api/admin/** ADMIN only
 * (enforced in SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    /** Standard and express shipping prices for the cart, delivered to this address. */
    @PostMapping("/api/customer/shipping/quote")
    public QuoteResponse quote(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody QuoteRequest request) {
        return shippingService.quote(user.getId(), request);
    }

    /** Track one of your shipments by its tracking number. */
    @GetMapping("/api/customer/track/{trackingNumber}")
    public TrackingResponse track(@AuthenticationPrincipal UserPrincipal user, @PathVariable String trackingNumber) {
        return shippingService.track(user.getId(), trackingNumber);
    }

    /** Shipping dashboard: status = PENDING|SHIPPED|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED|CANCELLED|RETURNED. */
    @GetMapping("/api/admin/shipping")
    public PageResponse<ShipmentResponse> list(@RequestParam(required = false) ShippingStatus status,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return shippingService.list(status, search, page, size);
    }

    @GetMapping("/api/admin/shipping/summary")
    public SummaryResponse summary() {
        return shippingService.summary();
    }

    @GetMapping("/api/admin/shipping/{orderId}")
    public ShipmentResponse get(@PathVariable Long orderId) {
        return shippingService.get(orderId);
    }

    /** Update courier service, tracking number and status: { courierService, trackingNumber, status, note }. */
    @PutMapping("/api/admin/shipping/{orderId}")
    public ShipmentResponse update(@PathVariable Long orderId, @Valid @RequestBody UpdateRequest request) {
        return shippingService.update(orderId, request);
    }
}
