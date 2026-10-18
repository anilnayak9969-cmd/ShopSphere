package com.example.ecommerce.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.delivery.DeliveryDashboardResponse;
import com.example.ecommerce.dtos.delivery.DeliveryJobResponse;
import com.example.ecommerce.dtos.delivery.DeliveryPartnerProfileRequest;
import com.example.ecommerce.dtos.delivery.DeliveryStatusRequest;
import com.example.ecommerce.dtos.delivery.DeliveryPartnerResponse;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.DeliveryPartnerService;
import com.example.ecommerce.services.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Delivery partner's own account (DELIVERY_PARTNER role only, see SecurityConfig). */
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryPartnerController {

    private final DeliveryPartnerService deliveryPartnerService;
    private final OrderService orderService;

    @GetMapping("/profile")
    public DeliveryPartnerResponse profile(@AuthenticationPrincipal UserPrincipal partner) {
        return deliveryPartnerService.get(partner.getId());
    }

    @PutMapping("/profile")
    public DeliveryPartnerResponse updateProfile(@AuthenticationPrincipal UserPrincipal partner,
                                                 @Valid @RequestBody DeliveryPartnerProfileRequest request) {
        return deliveryPartnerService.update(partner.getId(), request);
    }

    @GetMapping("/dashboard")
    public DeliveryDashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal partner) {
        return orderService.partnerDashboard(partner.getId());
    }

    /** Packed parcels waiting for a partner (customer contact details hidden until accepted). */
    @GetMapping("/pickups")
    public PageResponse<DeliveryJobResponse> pickups(@AuthenticationPrincipal UserPrincipal partner,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return orderService.partnerPickups(partner.getId(), page, size);
    }

    /** state=active (on the way) or completed (delivered history). */
    @GetMapping("/jobs")
    public PageResponse<DeliveryJobResponse> jobs(@AuthenticationPrincipal UserPrincipal partner,
                                                  @RequestParam(defaultValue = "active") String state,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return orderService.partnerJobs(partner.getId(), "completed".equalsIgnoreCase(state), page, size);
    }

    @PostMapping("/jobs/{orderItemId}/accept")
    public DeliveryJobResponse accept(@AuthenticationPrincipal UserPrincipal partner, @PathVariable Long orderItemId) {
        return orderService.acceptPickup(partner.getId(), orderItemId);
    }

    @PatchMapping("/jobs/{orderItemId}/status")
    public DeliveryJobResponse updateStatus(@AuthenticationPrincipal UserPrincipal partner, @PathVariable Long orderItemId,
                                            @Valid @RequestBody DeliveryStatusRequest request) {
        return orderService.updatePartnerJob(partner.getId(), orderItemId, request.status());
    }
}
