package com.example.ecommerce.services;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.returns.ReturnCreateRequest;
import com.example.ecommerce.dtos.returns.ReturnResponse;
import com.example.ecommerce.enums.ReturnStatus;

public interface ReturnService {

    ReturnResponse requestReturn(Long customerId, ReturnCreateRequest request);

    PageResponse<ReturnResponse> customerReturns(Long customerId, int page, int size);

    PageResponse<ReturnResponse> sellerReturns(Long sellerId, ReturnStatus status, int page, int size);

    PageResponse<ReturnResponse> adminReturns(ReturnStatus status, int page, int size);

    /** sellerId = null means an admin is deciding (admins may decide any return). */
    ReturnResponse decide(Long sellerId, Long returnId, boolean approve, String note);
}
