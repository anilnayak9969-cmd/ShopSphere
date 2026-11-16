package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.order.PaymentResponse;
import com.example.ecommerce.dtos.order.PaymentSummaryResponse;
import com.example.ecommerce.dtos.order.RefundRequest;
import com.example.ecommerce.dtos.order.RefundResponse;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.entites.Refund;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.RefundRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.PaymentAdminService;
import com.example.ecommerce.services.RefundService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentAdminServiceImpl implements PaymentAdminService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final UserRepository userRepository;
    private final RefundService refundService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(PaymentStatus status, boolean refundedOnly, PaymentMethod method,
                                              String search, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.of(paymentRepository.dashboard(status, refundedOnly, method, term, pageable(page, size)),
                p -> PaymentResponse.from(p, true));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummaryResponse summary() {
        BigDecimal online = paymentRepository.sumAmountByMethodAndStatus(PaymentMethod.RAZORPAY, PaymentStatus.SUCCESS)
                .add(paymentRepository.sumAmountByMethodAndStatus(PaymentMethod.RAZORPAY, PaymentStatus.REFUNDED));
        BigDecimal cash = paymentRepository.sumAmountByMethodAndStatus(PaymentMethod.COD, PaymentStatus.SUCCESS)
                .add(paymentRepository.sumAmountByMethodAndStatus(PaymentMethod.COD, PaymentStatus.REFUNDED));
        BigDecimal collected = online.add(cash);
        BigDecimal refunded = paymentRepository.sumRefunded();
        return new PaymentSummaryResponse(money(collected), money(online), money(cash), money(refunded),
                money(collected.subtract(refunded)),
                paymentRepository.countByStatus(PaymentStatus.SUCCESS),
                paymentRepository.countByStatus(PaymentStatus.FAILED),
                paymentRepository.countByStatus(PaymentStatus.PENDING),
                money(paymentRepository.sumAmountByMethodAndStatus(PaymentMethod.COD, PaymentStatus.PENDING)),
                refundRepository.countByStatus(Refund.Status.PROCESSED));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> refunds(int page, int size) {
        return PageResponse.of(refundRepository.history(pageable(page, size)), RefundResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> refundsOf(Long paymentId) {
        return refundRepository.findByPaymentPaymentIdOrderByCreatedAtAsc(paymentId).stream()
                .map(RefundResponse::from).toList();
    }

    /** noRollbackFor: a refused or failed Razorpay refund is still written to the refund log. */
    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public PaymentResponse issueRefund(Long adminId, Long paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment " + paymentId + " was not found."));
        CustomerOrder order = payment.getOrder();
        BigDecimal refundable = RefundService.refundable(payment);
        if (payment.getStatus() != PaymentStatus.SUCCESS || refundable.signum() <= 0) {
            throw ApiException.badRequest("NOTHING_TO_REFUND", "This payment has no collected amount left to refund.");
        }
        if (!PaymentResponse.isRefundEligible(payment, refundable)) {
            throw ApiException.badRequest("REFUND_NOT_ALLOWED",
                    "Refunds can be issued only for cancelled or returned orders.");
        }
        Payment latest = paymentRepository.findTopByOrderOrderIdOrderByCreatedAtDesc(order.getOrderId()).orElse(null);
        if (latest == null || !latest.getPaymentId().equals(paymentId)) {
            throw ApiException.badRequest("REFUND_NOT_ALLOWED", "Refund the order's latest payment instead.");
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(refundable) > 0) {
            throw ApiException.badRequest("REFUND_TOO_LARGE", "You can refund at most ₹" + refundable + " on this payment.");
        }
        String adminName = userRepository.findById(adminId).map(User::getFullName).orElse("Admin");

        RefundService.Outcome outcome = refundService.refund(order, amount, false, request.reason(), adminName);
        if (outcome == RefundService.Outcome.FAILED) {
            throw ApiException.badRequest("REFUND_FAILED",
                    "Razorpay could not process the refund. It is logged as failed; try again or refund from the Razorpay dashboard.");
        }
        if (outcome == RefundService.Outcome.NOT_REQUIRED) {
            throw ApiException.badRequest("NOTHING_TO_REFUND", "This payment has no collected amount left to refund.");
        }
        notificationService.notify(order.getCustomer(), "Refund issued",
                "A refund of ₹" + amount + " for order " + order.getOrderNumber() + " has been issued"
                        + (outcome == RefundService.Outcome.REFUNDED
                        ? " to your original payment method. It usually reaches you in 5-7 working days."
                        : ". Our team will transfer it to you."),
                "/account/payments");
        return PaymentResponse.from(paymentRepository.saveAndFlush(payment), true);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }
}
