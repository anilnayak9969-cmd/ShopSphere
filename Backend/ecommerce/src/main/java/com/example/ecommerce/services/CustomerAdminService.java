package com.example.ecommerce.services;

import com.example.ecommerce.dtos.admin.CustomerCreateRequest;
import com.example.ecommerce.dtos.admin.CustomerDetailResponse;
import com.example.ecommerce.dtos.admin.CustomerResponse;
import com.example.ecommerce.dtos.admin.CustomerSummaryResponse;
import com.example.ecommerce.dtos.admin.CustomerUpdateRequest;
import com.example.ecommerce.dtos.common.PageResponse;

/** Customer Management module (admin): view, add, update and deactivate customer accounts. */
public interface CustomerAdminService {

    /** Customer dashboard. active = true/false shows only active or inactive customers; null shows both. */
    PageResponse<CustomerResponse> list(String search, Boolean active, int page, int size);

    CustomerSummaryResponse summary();

    /** Profile, saved addresses and latest orders of one customer. */
    CustomerDetailResponse get(Long customerId);

    /** Admin adds a customer by hand. The account is verified and can sign in at once with the given password. */
    CustomerResponse create(Long adminId, CustomerCreateRequest request);

    /** Admin edits name, email, phone and account status. */
    CustomerResponse update(Long customerId, CustomerUpdateRequest request);

    /** Soft delete (active = false) or reactivate. The customer's data and orders are kept. */
    CustomerResponse setActive(Long customerId, boolean active);
}
