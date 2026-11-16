package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.ecommerce.dtos.order.OrderResponse;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.OrderItemEvent;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.OrderStatusGroup;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.DeliveryPartnerProfileRepository;
import com.example.ecommerce.repositories.OrderItemEventRepository;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.ReturnRequestRepository;
import com.example.ecommerce.repositories.SellerProfileRepository;
import com.example.ecommerce.services.LiveEventService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OrderSupport;
import com.example.ecommerce.services.PricingPolicy;
import com.example.ecommerce.services.RefundService;
import com.example.ecommerce.services.SettingsService;

/** Order Management module: taxes, dashboard groups, admin status jumps and cancellation rules. */
@ExtendWith(MockitoExtension.class)
class OrderManagementTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ReturnRequestRepository returnRepository;
    @Mock private OrderSupport orderSupport;
    @Mock private RefundService refundService;
    @Mock private NotificationService notificationService;
    @Mock private SettingsService settingsService;
    @Mock private SellerProfileRepository sellerProfileRepository;
    @Mock private DeliveryPartnerProfileRepository deliveryPartnerProfileRepository;
    @Mock private OrderItemEventRepository orderItemEventRepository;
    @Mock private LiveEventService liveEventService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void settings() {
        lenient().when(settingsService.get()).thenReturn(new PlatformSettings());
        lenient().when(refundService.refund(any(), any(), anyBoolean())).thenReturn(RefundService.Outcome.NOT_REQUIRED);
        lenient().when(orderSupport.refundShare(any(), any())).thenAnswer(inv -> ((OrderItem) inv.getArgument(1)).getLineTotal());
    }

    @Test
    void totalIncludesGstAtEighteenPercent() {
        assertThat(PricingPolicy.includedGst(new BigDecimal("1180.00"))).isEqualByComparingTo("180.00");
        assertThat(PricingPolicy.includedGst(new BigDecimal("2499.00"))).isEqualByComparingTo("381.20");
        assertThat(PricingPolicy.includedGst(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
    }

    @Test
    void dashboardGroupsMatchTheAssignmentFilters() {
        assertThat(OrderStatusGroup.PENDING.statuses())
                .contains(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PACKED)
                .doesNotContain(OrderStatus.SHIPPED, OrderStatus.CANCELLED);
        assertThat(OrderStatusGroup.SHIPPED.statuses()).containsExactlyInAnyOrder(OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY);
        assertThat(OrderStatusGroup.CANCELLED.statuses()).containsExactly(OrderStatus.CANCELLED);
    }

    @Test
    void adminCanMovePendingOrderStraightToShipped() {
        CustomerOrder order = order(OrderStatus.PLACED);
        when(orderRepository.findWithItemsByOrderId(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.adminUpdateStatus(1L, OrderStatus.SHIPPED, "Sent with ShopSphere");

        OrderItem item = order.getItems().get(0);
        assertThat(item.getItemStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(item.getCourierName()).isEqualTo("ShopSphere Delivery");
        assertThat(item.getTrackingNumber()).isEqualTo("SSD-11");
        // Confirmed, packed and shipped are each recorded in the tracking history...
        verify(orderItemEventRepository, times(3)).save(any(OrderItemEvent.class));
        // ...but the customer gets one notification, for the final status.
        verify(notificationService, times(1)).notify(eq(order.getCustomer()), eq("Order shipped"), anyString(), anyString());
        assertThat(response.orderId()).isEqualTo(1L);
    }

    @Test
    void adminCannotMoveAnOrderBackwards() {
        CustomerOrder order = order(OrderStatus.SHIPPED);
        when(orderRepository.findWithItemsByOrderId(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.adminUpdateStatus(1L, OrderStatus.PACKED, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No item of this order can move");
    }

    @Test
    void customerCanCancelUntilTheOrderIsShipped() {
        CustomerOrder order = order(OrderStatus.PACKED);
        when(orderRepository.findByOrderIdAndCustomerId(1L, 5L)).thenReturn(Optional.of(order));

        orderService.cancelByCustomer(5L, 1L);

        assertThat(order.getItems().get(0).getItemStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderSupport).restock(any());
    }

    @Test
    void shippedOrderCannotBeCancelled() {
        CustomerOrder order = order(OrderStatus.SHIPPED);
        when(orderRepository.findByOrderIdAndCustomerId(1L, 5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByCustomer(5L, 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already been shipped");
        verify(orderSupport, never()).restock(any());
    }

    @Test
    void adminCancelIsRefusedOnceEverythingHasShipped() {
        CustomerOrder order = order(OrderStatus.SHIPPED);
        when(orderRepository.findWithItemsByOrderId(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.adminUpdateStatus(1L, OrderStatus.CANCELLED, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("can be cancelled");
    }

    @Test
    void shippingAddressIsOneReadableLine() {
        CustomerOrder order = order(OrderStatus.PLACED);
        assertThat(order.getShippingAddress()).isEqualTo("Priya Sharma, 12 MG Road, Flat 4B, Pune, Maharashtra 411001");
    }

    // ---------------------------------------------------------------- helpers

    private static CustomerOrder order(OrderStatus itemStatus) {
        User customer = new User();
        customer.setId(5L);
        customer.setFullName("Priya Sharma");
        customer.setEmail("priya@example.com");
        User seller = new User();
        seller.setId(7L);
        seller.setStoreName("UrbanNest Home");

        CustomerOrder order = new CustomerOrder();
        order.setOrderId(1L);
        order.setOrderNumber("SS261116-TEST1");
        order.setCustomer(customer);
        order.setStatus(itemStatus);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setSubtotal(new BigDecimal("2499.00"));
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("2499.00"));
        order.setShippingName("Priya Sharma");
        order.setShippingPhone("9876543210");
        order.setAddressLine1("12 MG Road");
        order.setAddressLine2("Flat 4B");
        order.setCity("Pune");
        order.setState("Maharashtra");
        order.setPincode("411001");

        Product product = new Product();
        product.setProductId(79L);
        OrderItem item = new OrderItem();
        item.setOrderItemId(11L);
        item.setProduct(product);
        item.setSeller(seller);
        item.setProductName("Nordic Dome Pendant Light (White)");
        item.setStoreName("UrbanNest Home");
        item.setUnitPrice(new BigDecimal("2499.00"));
        item.setQuantity(1);
        item.setLineTotal(new BigDecimal("2499.00"));
        item.setItemStatus(itemStatus);
        order.addItem(item);
        return order;
    }
}
