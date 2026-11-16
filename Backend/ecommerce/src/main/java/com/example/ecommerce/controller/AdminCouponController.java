package com.example.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.common.StatusUpdateRequest;
import com.example.ecommerce.dtos.coupon.CouponRequest;
import com.example.ecommerce.dtos.coupon.CouponResponse;
import com.example.ecommerce.enums.CouponStatus;
import com.example.ecommerce.services.CouponService;
import com.example.ecommerce.services.CouponService.CouponSummary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Coupon and Discount Management (admin). */
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    /** Dashboard. status = ACTIVE | SCHEDULED | EXPIRED | INACTIVE | USED_UP | DELETED; empty = all not deleted. */
    @GetMapping
    public PageResponse<CouponResponse> list(@RequestParam(required = false) CouponStatus status,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return couponService.list(status, search, page, size);
    }

    @GetMapping("/summary")
    public CouponSummary summary() {
        return couponService.summary();
    }

    @GetMapping("/{id}")
    public CouponResponse get(@PathVariable Long id) {
        return couponService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CouponRequest request) {
        return couponService.create(request);
    }

    @PutMapping("/{id}")
    public CouponResponse update(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return couponService.update(id, request);
    }

    /** Activate / deactivate (the assignment's Boolean status column). */
    @PatchMapping("/{id}/status")
    public CouponResponse status(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return couponService.setActive(id, request.active());
    }

    /** Soft delete: the row and its redemption history are kept. */
    @DeleteMapping("/{id}")
    public CouponResponse delete(@PathVariable Long id) {
        return couponService.delete(id);
    }

    @PatchMapping("/{id}/restore")
    public CouponResponse restore(@PathVariable Long id) {
        return couponService.restore(id);
    }
}
