package com.example.ecommerce.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.order.PaymentResponse;
import com.example.ecommerce.dtos.order.PaymentSummaryResponse;
import com.example.ecommerce.dtos.order.RefundRequest;
import com.example.ecommerce.dtos.order.RefundResponse;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.PaymentAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Payment Management module: summary, refund log and admin-issued refunds. The payment list itself is
 * GET /api/admin/payments in AdminController. ADMIN only (SecurityConfig protects /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentAdminService paymentAdminService;

    @GetMapping("/payments/summary")
    public PaymentSummaryResponse summary() {
        return paymentAdminService.summary();
    }

    /** Refund log: every refund transaction, newest first. */
    @GetMapping("/refunds")
    public PageResponse<RefundResponse> refunds(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return paymentAdminService.refunds(page, size);
    }

    @GetMapping("/payments/{id}/refunds")
    public List<RefundResponse> refundsOf(@PathVariable Long id) {
        return paymentAdminService.refundsOf(id);
    }

    /** Issue a refund for a cancelled or returned order: { "amount": 49.00, "reason": "…" }. */
    @PostMapping("/payments/{id}/refund")
    public PaymentResponse refund(@AuthenticationPrincipal UserPrincipal admin, @PathVariable Long id,
                                  @Valid @RequestBody RefundRequest request) {
        return paymentAdminService.issueRefund(admin.getId(), id, request);
    }
}
