package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.example.ecommerce.dtos.order.OrderResponse;
import com.example.ecommerce.dtos.order.VerifyPaymentRequest;
import com.example.ecommerce.entites.Category;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CartRepository;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.CouponService;
import com.example.ecommerce.services.EmailService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OrderSupport;
import com.example.ecommerce.services.RazorpayGateway;
import com.example.ecommerce.services.RefundService;
import com.example.ecommerce.services.SettingsService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutServiceImplTest {

    private static final long CUSTOMER_ID = 7L;
    private static final VerifyPaymentRequest REQUEST =
            new VerifyPaymentRequest("order_ABC", "pay_XYZ", "signature");

    @Mock private UserRepository userRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RazorpayGateway razorpayGateway;
    @Mock private EmailService emailService;
    @Mock private CouponService couponService;
    @Mock private SettingsService settingsService;
    @Mock private NotificationService notificationService;
    @Mock private RefundService refundService;

    private CheckoutServiceImpl checkoutService;
    private Product product;
    private Payment payment;
    private CustomerOrder order;

    @BeforeEach
    void setUp() {
        // The real OrderSupport is used so stock reservation is genuinely exercised.
        OrderSupport orderSupport = new OrderSupport(productRepository, paymentRepository);
        checkoutService = new CheckoutServiceImpl(userRepository, cartRepository, orderRepository, paymentRepository,
                razorpayGateway, emailService, couponService, settingsService, notificationService, orderSupport,
                refundService);

        User customer = new User();
        customer.setId(CUSTOMER_ID);
        customer.setFullName("Riya");
        customer.setEmail("riya@example.com");
        User seller = new User();
        seller.setId(2L);
        seller.setStoreName("Demo Store");

        Category category = new Category();
        category.setCategoryId(1);
        category.setStatus(true);

        product = new Product();
        product.setProductId(100L);
        product.setProductName("Aura Headphones");
        product.setCategory(category);
        product.setSeller(seller);
        product.applyPricing(new BigDecimal("1000"), 10);
        product.setStockQuantity(5);

        order = new CustomerOrder();
        order.setOrderId(1L);
        order.setOrderNumber("SS261016-TEST01");
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setSubtotal(new BigDecimal("1800.00"));
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("1800.00"));
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setSeller(seller);
        item.setProductName(product.getProductName());
        item.setQuantity(2);
        item.setUnitPrice(product.getSellingPrice());
        item.setLineTotal(new BigDecimal("1800.00"));
        order.addItem(item);

        payment = new Payment();
        payment.setOrder(order);
        payment.setRazorpayOrderId("order_ABC");
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.CREATED);

        when(paymentRepository.lockByRazorpayOrderId("order_ABC")).thenReturn(Optional.of(payment));
        when(productRepository.lockByIds(any())).thenReturn(List.of(product));
    }

    @Test
    void validSignaturePlacesOrderDeductsStockAndNotifies() {
        when(razorpayGateway.isSignatureValid("order_ABC", "pay_XYZ", "signature")).thenReturn(true);

        OrderResponse response = checkoutService.verifyPayment(CUSTOMER_ID, REQUEST);

        assertThat(response.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getItems().get(0).getItemStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getRazorpayPaymentId()).isEqualTo("pay_XYZ");
        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(product.getSoldCount()).isEqualTo(2);
        verify(couponService).recordRedemption(order);
        verify(emailService).sendOrderConfirmation(order);
        verify(notificationService).notify(eq(order.getCustomer()), eq("Order placed"), anyString(), anyString());
    }

    @Test
    void invalidSignatureMarksPaymentFailedAndKeepsStock() {
        when(razorpayGateway.isSignatureValid(anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> checkoutService.verifyPayment(CUSTOMER_ID, REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("PAYMENT_VERIFICATION_FAILED"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(product.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void verifyingTwiceDoesNotDeductStockAgain() {
        payment.setStatus(PaymentStatus.SUCCESS);
        order.setStatus(OrderStatus.PLACED);

        OrderResponse response = checkoutService.verifyPayment(CUSTOMER_ID, REQUEST);

        assertThat(response.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(product.getStockQuantity()).isEqualTo(5);
        verify(razorpayGateway, never()).isSignatureValid(anyString(), anyString(), anyString());
        verify(productRepository, never()).lockByIds(any());
    }

    @Test
    void anotherCustomersPaymentIsNotFound() {
        assertThatThrownBy(() -> checkoutService.verifyPayment(999L, REQUEST))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void soldOutDuringPaymentCancelsAndRefunds() {
        product.setStockQuantity(1);
        when(razorpayGateway.isSignatureValid(anyString(), anyString(), anyString())).thenReturn(true);

        OrderResponse response = checkoutService.verifyPayment(CUSTOMER_ID, REQUEST);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getItems().get(0).getItemStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(1);
        verify(refundService).refund(order, order.getTotalAmount(), true);
        verify(couponService, never()).recordRedemption(any());
    }

    @Test
    void orderNumbersHaveExpectedFormat() {
        assertThat(CheckoutServiceImpl.generateOrderNumber()).matches("SS\\d{6}-[A-Z2-9]{6}");
    }
}
