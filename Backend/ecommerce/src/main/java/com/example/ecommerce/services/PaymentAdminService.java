package com.example.ecommerce.services;

import java.util.List;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.order.PaymentResponse;
import com.example.ecommerce.dtos.order.PaymentSummaryResponse;
import com.example.ecommerce.dtos.order.RefundRequest;
import com.example.ecommerce.dtos.order.RefundResponse;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;

/** Payment Management module (admin): transaction history, refunds and the refund log. */
public interface PaymentAdminService {

    /** Payment dashboard: every payment with id, amount, method and status. */
    PageResponse<PaymentResponse> list(PaymentStatus status, boolean refundedOnly, PaymentMethod method,
                                       String search, int page, int size);

    PaymentSummaryResponse summary();

    /** The refund log, newest first. */
    PageResponse<RefundResponse> refunds(int page, int size);

    /** Refunds of one payment. */
    List<RefundResponse> refundsOf(Long paymentId);

    /** Admin issues a refund for a cancelled or returned order. Logged in the refund log. */
    PaymentResponse issueRefund(Long adminId, Long paymentId, RefundRequest request);
}
