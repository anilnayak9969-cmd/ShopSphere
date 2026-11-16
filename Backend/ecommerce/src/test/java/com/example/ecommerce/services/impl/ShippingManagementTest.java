package com.example.ecommerce.services.impl;

import static com.example.ecommerce.services.impl.TestCatalog.customer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ecommerce.dtos.cart.CartResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.QuoteRequest;
import com.example.ecommerce.dtos.shipping.ShippingDtos.QuoteResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.ShipmentResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.TrackingResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.UpdateRequest;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.OrderItemEvent;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.entites.Shipment;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.DeliveryZone;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.ShippingMethod;
import com.example.ecommerce.enums.ShippingStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.OrderItemEventRepository;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.ShipmentRepository;
import com.example.ecommerce.services.CartService;
import com.example.ecommerce.services.CouponService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OrderService;
import com.example.ecommerce.services.OrderSupport;
import com.example.ecommerce.services.SettingsService;

/** Shipping Management module: quotes, admin shipping updates, and customer tracking. */
@ExtendWith(MockitoExtension.class)
class ShippingManagementTest {

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderItemEventRepository eventRepository;
    @Mock private CartService cartService;
    @Mock private CouponService couponService;
    @Mock private SettingsService settingsService;
    @Mock private OrderService orderService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ShippingServiceImpl shipping;

    private final User priya = customer(8L, "Priya Sharma");

    @BeforeEach
    void setUp() {
        lenient().when(settingsService.get()).thenReturn(new PlatformSettings());
        lenient().when(shipmentRepository.saveAndFlush(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------ quote

    @Test
    void quoteOffersStandardAndExpressForTheAddress() {
        when(cartService.getCart(8L)).thenReturn(cart("300.00", "1.20"));

        QuoteResponse q = shipping.quote(8L, new QuoteRequest("Nashik", "Maharashtra", "422001", null));

        assertThat(q.zone()).isEqualTo(DeliveryZone.NATIONAL);
        assertThat(q.options()).hasSize(2);
        // National ₹69 + 2 × ₹15 for 1.2 kg = ₹99; express = 2 × 99.
        assertThat(q.options().get(0).cost()).isEqualByComparingTo("99.00");
        assertThat(q.options().get(1).method()).isEqualTo(ShippingMethod.EXPRESS);
        assertThat(q.options().get(1).cost()).isEqualByComparingTo("198.00");
        assertThat(q.options().get(0).total()).isEqualByComparingTo("399.00");
    }

    @Test
    void quoteOfAnEmptyCartIsRefused() {
        when(cartService.getCart(8L)).thenReturn(cart("0.00", "0"));
        assertThatThrownBy(() -> shipping.quote(8L, new QuoteRequest("Pune", "Maharashtra", "411006", null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("empty");
    }

    // ----------------------------------------------------------------- update

    @Test
    void shippingAPendingOrderMovesItsItemsAndAppliesTheCourier() {
        CustomerOrder order = order(OrderStatus.PLACED, ShippingStatus.PENDING);
        stubOrder(order);
        List<String> trackingWhenShipped = new java.util.ArrayList<>();
        doAnswer(inv -> {
            // Like the real order service: an item with no courier yet falls back to ShopSphere Delivery.
            order.getItems().forEach(i -> {
                i.setItemStatus(OrderStatus.SHIPPED);
                if (i.getCourierName() == null) {
                    i.setCourierName("ShopSphere Delivery");
                    i.setTrackingNumber("SSD-" + i.getOrderItemId());
                }
                trackingWhenShipped.add(i.getTrackingNumber());
            });
            order.setStatus(OrderStatus.SHIPPED);
            return null;
        }).when(orderService).adminUpdateStatus(1L, OrderStatus.SHIPPED, null);

        ShipmentResponse r = shipping.update(1L, new UpdateRequest("Delhivery", "dlv1234567890", ShippingStatus.SHIPPED, null));

        verify(orderService).adminUpdateStatus(1L, OrderStatus.SHIPPED, null);
        // The items already carried the admin's tracking number when they were shipped (no SSD- fallback).
        assertThat(trackingWhenShipped).containsOnly("DLV1234567890");
        assertThat(r.shippingStatus()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(r.courierService()).isEqualTo("Delhivery");
        assertThat(r.trackingNumber()).isEqualTo("DLV1234567890");
        assertThat(order.getItems()).allSatisfy(i -> assertThat(i.getTrackingNumber()).isEqualTo("DLV1234567890"));
        verify(notificationService).notify(eq(priya), eq("Shipping update"), anyString(), eq("/track?number=DLV1234567890"));
    }

    @Test
    void inTransitIsRecordedWithoutChangingTheItems() {
        CustomerOrder order = order(OrderStatus.SHIPPED, ShippingStatus.SHIPPED);
        stubOrder(order);

        ShipmentResponse r = shipping.update(1L, new UpdateRequest("ShopSphere Delivery", "SSD-11", ShippingStatus.IN_TRANSIT, "Left Pune hub"));

        assertThat(r.shippingStatus()).isEqualTo(ShippingStatus.IN_TRANSIT);
        verify(orderService, never()).adminUpdateStatus(any(), any(), any());
        verify(eventRepository).save(any(OrderItemEvent.class));
        assertThat(order.getItems().get(0).getItemStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void aShipmentCannotGoBackwards() {
        CustomerOrder order = order(OrderStatus.DELIVERED, ShippingStatus.DELIVERED);
        stubOrder(order);
        assertThatThrownBy(() -> shipping.update(1L, new UpdateRequest("ShopSphere Delivery", "SSD-11", ShippingStatus.SHIPPED, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("cannot go back");
    }

    @Test
    void aTrackingNumberUsedByAnotherOrderIsRefused() {
        CustomerOrder order = order(OrderStatus.SHIPPED, ShippingStatus.SHIPPED);
        stubOrder(order);
        when(shipmentRepository.existsByTrackingNumberIgnoreCaseAndOrderOrderIdNot("SSD-99", 1L)).thenReturn(true);
        assertThatThrownBy(() -> shipping.update(1L, new UpdateRequest("ShopSphere Delivery", "SSD-99", null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancelledOrdersCannotBeShipped() {
        CustomerOrder order = order(OrderStatus.CANCELLED, ShippingStatus.CANCELLED);
        stubOrder(order);
        assertThatThrownBy(() -> shipping.update(1L, new UpdateRequest("Delhivery", "DLV123456", ShippingStatus.SHIPPED, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("cancelled or returned");
        verify(orderService, never()).adminUpdateStatus(any(), any(), any());
    }

    @Test
    void shipmentStatusFollowsTheOrder() {
        CustomerOrder order = order(OrderStatus.OUT_FOR_DELIVERY, ShippingStatus.IN_TRANSIT);
        order.getItems().get(0).setTrackingNumber("SSD-11");
        order.getItems().get(0).setCourierName("ShopSphere Delivery");
        OrderSupport.syncShipment(order);
        assertThat(order.getShipment().getShippingStatus()).isEqualTo(ShippingStatus.OUT_FOR_DELIVERY);
        assertThat(order.getShipment().getTrackingNumber()).isEqualTo("SSD-11");

        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        OrderSupport.syncShipment(order);
        assertThat(order.getShipment().getShippingStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(order.getShipment().getDeliveredAt()).isNotNull();
    }

    // ------------------------------------------------------------------ track

    @Test
    void customersTrackTheirOwnShipmentByTrackingNumber() {
        CustomerOrder order = order(OrderStatus.SHIPPED, ShippingStatus.SHIPPED);
        order.getShipment().setTrackingNumber("DLV1234567890");
        when(shipmentRepository.findFirstByTrackingNumberIgnoreCase("dlv1234567890")).thenReturn(Optional.of(order.getShipment()));
        when(eventRepository.findByOrderItemOrderItemIdInOrderByCreatedAtAscEventIdAsc(any())).thenReturn(List.of());

        TrackingResponse t = shipping.track(8L, " dlv1234567890 ");

        assertThat(t.orderNumber()).isEqualTo("SS261116-TEST1");
        assertThat(t.shippingStatus()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(t.history()).first().satisfies(h -> assertThat(h.description()).isEqualTo("Order placed"));
    }

    @Test
    void trackingHistoryShowsAStepSharedByTheWholeParcelOnce() {
        CustomerOrder order = order(OrderStatus.SHIPPED, ShippingStatus.SHIPPED);
        OrderItem second = new OrderItem();
        second.setOrderItemId(12L);
        second.setProductName("Tales from the Western Ghats");
        second.setQuantity(1);
        second.setItemStatus(OrderStatus.SHIPPED);
        order.addItem(second);
        when(shipmentRepository.findFirstByTrackingNumberIgnoreCase("SSD-11")).thenReturn(Optional.of(order.getShipment()));
        LocalDateTime at = LocalDateTime.of(2026, 11, 16, 10, 25, 5);
        OrderItem first = order.getItems().get(0);
        when(eventRepository.findByOrderItemOrderItemIdInOrderByCreatedAtAscEventIdAsc(any())).thenReturn(List.of(
                event(first, OrderStatus.CONFIRMED, "Confirmed by UrbanNest Home", at),
                event(first, OrderStatus.SHIPPED, "Shipped via Blue Dart", at),
                event(second, OrderStatus.CONFIRMED, "Confirmed by PageTurner Books", at),
                event(second, OrderStatus.SHIPPED, "Shipped via Blue Dart", at),
                event(first, OrderStatus.SHIPPED, "In transit with Blue Dart", at.plusSeconds(20)),
                event(second, OrderStatus.SHIPPED, "In transit with Blue Dart", at.plusSeconds(21))));

        TrackingResponse t = shipping.track(8L, "SSD-11");

        // The shared "Shipped" step is listed once, after both items were confirmed.
        assertThat(t.history()).extracting(h -> h.description()).containsExactly("Order placed",
                "Confirmed by UrbanNest Home", "Confirmed by PageTurner Books", "Shipped via Blue Dart",
                "In transit with Blue Dart");
        assertThat(t.history().get(1).productName()).isEqualTo("Nordic Dome Pendant Light");
        assertThat(t.history().get(3).productName()).isNull(); // the whole parcel
        assertThat(t.history().get(4).productName()).isNull();
    }

    private static OrderItemEvent event(OrderItem item, OrderStatus status, String description, LocalDateTime at) {
        OrderItemEvent e = new OrderItemEvent();
        e.setOrderItem(item);
        e.setStatus(status);
        e.setDescription(description);
        e.setCreatedAt(at);
        return e;
    }

    @Test
    void anotherCustomersTrackingNumberIsNotFound() {
        CustomerOrder order = order(OrderStatus.SHIPPED, ShippingStatus.SHIPPED);
        order.getShipment().setTrackingNumber("DLV1234567890");
        when(shipmentRepository.findFirstByTrackingNumberIgnoreCase("DLV1234567890")).thenReturn(Optional.of(order.getShipment()));

        assertThatThrownBy(() -> shipping.track(99L, "DLV1234567890"))
                .isInstanceOf(ApiException.class).hasMessageContaining("No shipment");
    }

    // ---------------------------------------------------------------- helpers

    private void stubOrder(CustomerOrder order) {
        when(shipmentRepository.findByOrderOrderId(1L)).thenReturn(Optional.of(order.getShipment()));
        when(orderRepository.findWithItemsByOrderId(1L)).thenReturn(Optional.of(order));
    }

    private CustomerOrder order(OrderStatus status, ShippingStatus shippingStatus) {
        CustomerOrder order = new CustomerOrder();
        order.setOrderId(1L);
        order.setOrderNumber("SS261116-TEST1");
        order.setCustomer(priya);
        order.setStatus(status);
        order.setCity("Pune");
        order.setState("Maharashtra");
        order.setPincode("411006");
        OrderItem item = new OrderItem();
        item.setOrderItemId(11L);
        item.setProductName("Nordic Dome Pendant Light");
        item.setQuantity(1);
        item.setItemStatus(status);
        order.addItem(item);
        Shipment shipment = new Shipment();
        shipment.setShippingId(5L);
        shipment.setShippingStatus(shippingStatus);
        shipment.setShippingCost(BigDecimal.ZERO);
        shipment.setShippingMethod(ShippingMethod.STANDARD);
        shipment.setDeliveryZone(DeliveryZone.METRO);
        if (shippingStatus.isShipped()) {
            shipment.setCourierService("ShopSphere Delivery");
            shipment.setTrackingNumber("SSD-11");
            item.setCourierName("ShopSphere Delivery");
            item.setTrackingNumber("SSD-11");
        }
        order.attachShipment(shipment);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusDays(1));
        return order;
    }

    private static CartResponse cart(String subtotal, String weight) {
        BigDecimal s = new BigDecimal(subtotal);
        return new CartResponse(s.signum() == 0 ? List.of() : List.of(new com.example.ecommerce.dtos.cart.CartItemResponse(
                1L, 62L, "Book", null, "PageTurner", s, s, 0, 1, s, 10, true, null, false, null)),
                1, s, BigDecimal.ZERO, s, new BigDecimal("499"), true, false, new BigDecimal(weight));
    }
}
