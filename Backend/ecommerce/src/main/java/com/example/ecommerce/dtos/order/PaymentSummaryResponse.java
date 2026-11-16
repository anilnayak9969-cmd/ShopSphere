package com.example.ecommerce.dtos.order;

import java.math.BigDecimal;

/** Summary cards on the admin Payment dashboard. */
public record PaymentSummaryResponse(
        /* Money collected: successful payments plus payments that were later refunded. */
        BigDecimal collected,
        BigDecimal collectedOnline,
        BigDecimal collectedCash,
        BigDecimal refunded,
        /* collected - refunded */
        BigDecimal net,
        long successful,
        long failed,
        long codDue,
        BigDecimal codDueAmount,
        long refundTransactions) {
}
