package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.ecommerce.dtos.order.PaymentResponse;
import com.example.ecommerce.dtos.order.RefundRequest;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.entites.Refund;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentInstrument;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.RefundRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.RazorpayGateway;
import com.example.ecommerce.services.RefundService;
import com.razorpay.RazorpayException;

/** Payment Management module: payment methods, the refund log and admin-issued refunds. */
@ExtendWith(MockitoExtension.class)
class PaymentManagementTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private RazorpayGateway razorpayGateway;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @Test
    void razorpayMethodsMapToPaymentInstruments() {
        assertThat(PaymentInstrument.fromRazorpay("card", null)).isEqualTo(PaymentInstrument.CARD);
        assertThat(PaymentInstrument.fromRazorpay("upi", null)).isEqualTo(PaymentInstrument.UPI);
        assertThat(PaymentInstrument.fromRazorpay("netbanking", null)).isEqualTo(PaymentInstrument.NETBANKING);
        assertThat(PaymentInstrument.fromRazorpay("wallet", "paypal")).isEqualTo(PaymentInstrument.PAYPAL);
        assertThat(PaymentInstrument.fromRazorpay("wallet", "paytm")).isEqualTo(PaymentInstrument.WALLET);
        assertThat(PaymentInstrument.fromRazorpay(null, null)).isEqualTo(PaymentInstrument.OTHER);
    }

    @Test
    void paymentDetailsNeverExposeFullCardOrUpiIds() throws Exception {
        JSONObject card = new JSONObject().put("method", "card")
                .put("card", new JSONObject().put("network", "Visa").put("type", "credit").put("last4", "1111"));
        assertThat(RazorpayGateway.detailsOf(card).detail()).isEqualTo("Visa credit ···· 1111");
        JSONObject upi = new JSONObject().put("method", "upi").put("vpa", "priya.sharma@okhdfc");
        assertThat(RazorpayGateway.detailsOf(upi).detail()).isEqualTo("pr···@okhdfc");
        JSONObject bank = new JSONObject().put("method", "netbanking").put("bank", "HDFC");
        assertThat(RazorpayGateway.detailsOf(bank).detail()).isEqualTo("HDFC");
    }

    @Test
    void everyRazorpayRefundIsLoggedWithItsRefundId() throws Exception {
        RefundService refundService = new RefundService(paymentRepository, razorpayGateway, refundRepository);
        Payment payment = payment(PaymentMethod.RAZORPAY, "1000.00", OrderStatus.CANCELLED);
        when(paymentRepository.findTopByOrderOrderIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(payment));
        when(razorpayGateway.refund("pay_ABC", 40000L)).thenReturn("rfnd_XYZ");

        RefundService.Outcome outcome = refundService.refund(payment.getOrder(), new BigDecimal("400.00"), false,
                "Item cancelled", "System");

        assertThat(outcome).isEqualTo(RefundService.Outcome.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("400.00");
        ArgumentCaptor<Refund> logged = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(logged.capture());
        assertThat(logged.getValue().getRazorpayRefundId()).isEqualTo("rfnd_XYZ");
        assertThat(logged.getValue().getStatus()).isEqualTo(Refund.Status.PROCESSED);
        assertThat(logged.getValue().getMode()).isEqualTo(Refund.Mode.RAZORPAY);
    }

    @Test
    void aFailedGatewayRefundIsLoggedAsFailedAndNotCounted() throws Exception {
        RefundService refundService = new RefundService(paymentRepository, razorpayGateway, refundRepository);
        Payment payment = payment(PaymentMethod.RAZORPAY, "1000.00", OrderStatus.CANCELLED);
        when(paymentRepository.findTopByOrderOrderIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(payment));
        when(razorpayGateway.refund(anyString(), anyLong())).thenThrow(new RazorpayException("gateway down"));

        RefundService.Outcome outcome = refundService.refund(payment.getOrder(), new BigDecimal("1000.00"), true);

        assertThat(outcome).isEqualTo(RefundService.Outcome.FAILED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("0");
        ArgumentCaptor<Refund> logged = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(logged.capture());
        assertThat(logged.getValue().getStatus()).isEqualTo(Refund.Status.FAILED);
    }

    @Test
    void adminCanRefundTheRestOfAReturnedCashOrder() {
        PaymentAdminServiceImpl service = adminService();
        Payment payment = payment(PaymentMethod.COD, "363.10", OrderStatus.RETURNED);
        payment.setRefundedAmount(new BigDecimal("314.10")); // the item was refunded when the return was approved
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findTopByOrderOrderIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        PaymentResponse response = service.issueRefund(1L, 5L, new RefundRequest(new BigDecimal("49.00"), "Delivery fee for the returned order"));

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.refundedAmount()).isEqualByComparingTo("363.10");
        assertThat(response.refundable()).isEqualByComparingTo("0");
        verify(notificationService).notify(any(User.class), eq("Refund issued"), anyString(), anyString());
    }

    @Test
    void refundsAreOnlyForCancelledOrReturnedOrders() {
        PaymentAdminServiceImpl service = adminService();
        Payment payment = payment(PaymentMethod.RAZORPAY, "1000.00", OrderStatus.DELIVERED);
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.issueRefund(1L, 5L, new RefundRequest(new BigDecimal("100"), "Goodwill refund")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only for cancelled or returned orders");
        verify(refundRepository, never()).save(any());
    }

    @Test
    void anAdminCannotRefundMoreThanWasPaid() {
        PaymentAdminServiceImpl service = adminService();
        Payment payment = payment(PaymentMethod.RAZORPAY, "1000.00", OrderStatus.CANCELLED);
        payment.setRefundedAmount(new BigDecimal("900.00"));
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findTopByOrderOrderIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.issueRefund(1L, 5L, new RefundRequest(new BigDecimal("200"), "Refund the rest")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at most ₹100.00");
    }

    // ---------------------------------------------------------------- helpers

    private PaymentAdminServiceImpl adminService() {
        RefundService refundService = new RefundService(paymentRepository, razorpayGateway, refundRepository);
        return new PaymentAdminServiceImpl(paymentRepository, refundRepository, userRepository, refundService,
                notificationService);
    }

    private static Payment payment(PaymentMethod method, String amount, OrderStatus orderStatus) {
        User customer = new User();
        customer.setId(8L);
        customer.setFullName("Priya Sharma");
        customer.setEmail("priya@example.com");
        CustomerOrder order = new CustomerOrder();
        order.setOrderId(1L);
        order.setOrderNumber("SS261116-TEST1");
        order.setCustomer(customer);
        order.setStatus(orderStatus);
        OrderItem item = new OrderItem();
        item.setItemStatus(orderStatus);
        order.addItem(item);
        Payment payment = new Payment();
        payment.setPaymentId(5L);
        payment.setOrder(order);
        payment.setMethod(method);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId(method == PaymentMethod.RAZORPAY ? "pay_ABC" : null);
        payment.setInstrument(method == PaymentMethod.COD ? PaymentInstrument.CASH : PaymentInstrument.CARD);
        return payment;
    }
}
