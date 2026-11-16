package com.example.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import com.example.ecommerce.dtos.admin.CustomerCreateRequest;
import com.example.ecommerce.dtos.admin.CustomerDetailResponse;
import com.example.ecommerce.dtos.admin.CustomerResponse;
import com.example.ecommerce.dtos.admin.CustomerSummaryResponse;
import com.example.ecommerce.dtos.admin.CustomerUpdateRequest;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.common.StatusUpdateRequest;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.CustomerAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Customer Management module. ADMIN only (SecurityConfig protects /api/admin/**). */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerAdminService customerAdminService;

    /** Customer dashboard: search by name, email or phone; active=true/false filters by status. */
    @GetMapping
    public PageResponse<CustomerResponse> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return customerAdminService.list(search, active, page, size);
    }

    @GetMapping("/summary")
    public CustomerSummaryResponse summary() {
        return customerAdminService.summary();
    }

    /** Profile, addresses and order history. */
    @GetMapping("/{id}")
    public CustomerDetailResponse get(@PathVariable Long id) {
        return customerAdminService.get(id);
    }

    /** Add a new customer by hand. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@AuthenticationPrincipal UserPrincipal admin,
                                   @Valid @RequestBody CustomerCreateRequest request) {
        return customerAdminService.create(admin.getId(), request);
    }

    /** Update contact details and account status. */
    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerUpdateRequest request) {
        return customerAdminService.update(id, request);
    }

    /** Deactivate (soft delete) or reactivate: {"active": false|true}. */
    @PatchMapping("/{id}/status")
    public CustomerResponse status(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return customerAdminService.setActive(id, request.active());
    }
}
