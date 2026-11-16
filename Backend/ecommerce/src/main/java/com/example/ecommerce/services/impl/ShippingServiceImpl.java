package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.cart.CartResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.QuoteOption;
import com.example.ecommerce.dtos.shipping.ShippingDtos.QuoteRequest;
import com.example.ecommerce.dtos.shipping.ShippingDtos.QuoteResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.ShipmentResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.SummaryResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.TrackedItem;
import com.example.ecommerce.dtos.shipping.ShippingDtos.TrackingLine;
import com.example.ecommerce.dtos.shipping.ShippingDtos.TrackingResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.UpdateRequest;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.OrderItemEvent;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.entites.Shipment;
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
import com.example.ecommerce.services.ShippingCalculator;
import com.example.ecommerce.services.ShippingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements ShippingService {

    /** Forward path of an order line; an admin may only move a shipment forward along it. */
    private static final List<OrderStatus> PATH = List.of(OrderStatus.PLACED, OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING, OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED);
    private static final EnumSet<OrderStatus> SHIPPED_ITEMS =
            EnumSet.of(OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);
    private static final Map<ShippingStatus, Integer> RANK = Map.of(ShippingStatus.PENDING, 0, ShippingStatus.SHIPPED, 1,
            ShippingStatus.IN_TRANSIT, 2, ShippingStatus.OUT_FOR_DELIVERY, 3, ShippingStatus.DELIVERED, 4);

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemEventRepository eventRepository;
    private final CartService cartService;
    private final CouponService couponService;
    private final SettingsService settingsService;
    private final OrderService orderService;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ quote

    @Override
    @Transactional(readOnly = true)
    public QuoteResponse quote(Long customerId, QuoteRequest request) {
        CartResponse cart = cartService.getCart(customerId);
        if (cart.items().isEmpty() || cart.subtotal().signum() == 0) {
            throw ApiException.badRequest("CART_EMPTY", "Your cart is empty.");
        }
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        String code = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            var applied = couponService.resolve(customerId, request.couponCode(), CouponService.linesOf(cart));
            discount = applied.discount();
            code = applied.coupon().getCode();
        }
        BigDecimal afterDiscount = cart.subtotal().subtract(discount);
        DeliveryZone zone = ShippingCalculator.zoneFor(request.city(), request.state(), request.pincode());
        PlatformSettings settings = settingsService.get();
        List<QuoteOption> options = new ArrayList<>();
        for (ShippingMethod method : ShippingMethod.values()) {
            var q = ShippingCalculator.quote(afterDiscount, cart.weightKg(), zone, method, settings, LocalDate.now());
            options.add(new QuoteOption(method, method.label(), q.cost(), q.free(), q.minDays(), q.maxDays(),
                    q.estimatedDelivery(), afterDiscount.add(q.cost())));
        }
        return new QuoteResponse(zone, zone.label(), cart.weightKg(), cart.subtotal(), discount, code, options);
    }

    // -------------------------------------------------------------- dashboard

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShipmentResponse> list(ShippingStatus status, String search, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        var statuses = status == null ? EnumSet.allOf(ShippingStatus.class) : EnumSet.of(status);
        return PageResponse.of(shipmentRepository.search(status == null, statuses, term,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))), ShipmentResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public SummaryResponse summary() {
        Map<ShippingStatus, Long> counts = new EnumMap<>(ShippingStatus.class);
        for (Object[] row : shipmentRepository.statusCounts()) {
            counts.put((ShippingStatus) row[0], ((Number) row[1]).longValue());
        }
        return new SummaryResponse(counts.getOrDefault(ShippingStatus.PENDING, 0L),
                counts.getOrDefault(ShippingStatus.SHIPPED, 0L), counts.getOrDefault(ShippingStatus.IN_TRANSIT, 0L),
                counts.getOrDefault(ShippingStatus.OUT_FOR_DELIVERY, 0L), counts.getOrDefault(ShippingStatus.DELIVERED, 0L),
                counts.getOrDefault(ShippingStatus.CANCELLED, 0L) + counts.getOrDefault(ShippingStatus.RETURNED, 0L),
                shipmentRepository.countExpress(), shipmentRepository.shippingRevenue().setScale(2));
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse get(Long orderId) {
        return ShipmentResponse.from(findShipment(orderId));
    }

    // ----------------------------------------------------------------- update

    @Override
    @Transactional
    public ShipmentResponse update(Long orderId, UpdateRequest request) {
        Shipment shipment = findShipment(orderId);
        CustomerOrder order = orderRepository.findWithItemsByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Order " + orderId + " was not found."));
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw ApiException.badRequest("ORDER_NOT_PLACED", "This order was never paid, so there is nothing to ship.");
        }
        if (shipment.getShippingStatus() == ShippingStatus.CANCELLED || shipment.getShippingStatus() == ShippingStatus.RETURNED) {
            throw ApiException.badRequest("NOT_SHIPPABLE", "This order was cancelled or returned, so it cannot be shipped.");
        }
        ShippingStatus target = request.status() == null ? shipment.getShippingStatus() : request.status();
        if (!RANK.containsKey(target) || target == ShippingStatus.PENDING) {
            throw ApiException.badRequest("SHIP_FIRST",
                    "Shipping details are added when the order is shipped — choose Shipped, In transit, Out for delivery or Delivered.");
        }
        if (RANK.get(target) < RANK.get(shipment.getShippingStatus())) {
            throw ApiException.badRequest("STATUS_BACKWARDS", "A shipment cannot go back from "
                    + label(shipment.getShippingStatus()) + " to " + label(target) + ".");
        }
        String courier = request.courierService().trim();
        String tracking = request.trackingNumber().trim().toUpperCase(Locale.ROOT);
        if (shipmentRepository.existsByTrackingNumberIgnoreCaseAndOrderOrderIdNot(tracking, orderId)) {
            throw ApiException.conflict("TRACKING_IN_USE", "Tracking number " + tracking + " is already used by another order.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();

        // Move the order's items forward (this records each step, notifies, and marks COD paid on delivery).
        OrderStatus itemTarget = target == ShippingStatus.IN_TRANSIT ? OrderStatus.SHIPPED : OrderStatus.valueOf(target.name());
        if (PATH.indexOf(order.getStatus()) >= 0 && PATH.indexOf(order.getStatus()) < PATH.indexOf(itemTarget)) {
            // Items that have not left the warehouse ship with the admin's courier and tracking number
            // (otherwise the order service would fall back to ShopSphere Delivery and an SSD- number).
            for (OrderItem item : order.getItems()) {
                int step = PATH.indexOf(item.getItemStatus());
                if (step >= 0 && step < PATH.indexOf(OrderStatus.SHIPPED)) {
                    item.setCourierName(courier);
                    item.setTrackingNumber(tracking);
                }
            }
            orderService.adminUpdateStatus(orderId, itemTarget, note);
        }

        // Apply the courier and tracking number to every item that has left the warehouse.
        boolean detailsChanged = !courier.equals(shipment.getCourierService()) || !tracking.equals(shipment.getTrackingNumber());
        for (OrderItem item : order.getItems()) {
            if (SHIPPED_ITEMS.contains(item.getItemStatus())
                    && (!courier.equals(item.getCourierName()) || !tracking.equals(item.getTrackingNumber()))) {
                item.setCourierName(courier);
                item.setTrackingNumber(tracking);
                recordEvent(item, item.getItemStatus(), "Shipping details updated: " + courier + " · tracking " + tracking);
            }
        }
        shipment.setCourierService(courier);
        shipment.setTrackingNumber(tracking);
        boolean nowInTransit = target == ShippingStatus.IN_TRANSIT && shipment.getShippingStatus() != ShippingStatus.IN_TRANSIT;
        if (target == ShippingStatus.IN_TRANSIT) {
            shipment.setShippingStatus(ShippingStatus.IN_TRANSIT);
            if (nowInTransit) {
                order.getItems().stream().filter(i -> i.getItemStatus() == OrderStatus.SHIPPED)
                        .forEach(i -> recordEvent(i, OrderStatus.SHIPPED, "In transit with " + courier
                                + (note == null ? "" : " — " + note)));
            }
        }
        OrderSupport.syncShipment(order);
        if (detailsChanged || nowInTransit) {
            notificationService.notify(order.getCustomer(), "Shipping update",
                    "Order " + order.getOrderNumber() + " — " + label(shipment.getShippingStatus()) + ": " + courier
                            + " · tracking " + tracking + ".", "/track?number=" + tracking);
        }
        return ShipmentResponse.from(shipmentRepository.saveAndFlush(shipment));
    }

    // ------------------------------------------------------------------ track

    @Override
    @Transactional(readOnly = true)
    public TrackingResponse track(Long customerId, String trackingNumber) {
        String number = trackingNumber == null ? "" : trackingNumber.trim();
        if (number.isEmpty()) {
            throw ApiException.badRequest("TRACKING_REQUIRED", "Enter a tracking number.");
        }
        CustomerOrder order = shipmentRepository.findFirstByTrackingNumberIgnoreCase(number).map(Shipment::getOrder)
                .or(() -> orderItemRepository.findFirstByTrackingNumberIgnoreCase(number).map(OrderItem::getOrder))
                .filter(o -> o.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("No shipment with tracking number " + number + " was found on your account."));
        Shipment shipment = order.getShipment();
        List<OrderItem> items = order.getItems();
        Map<Long, String> names = new java.util.HashMap<>();
        items.forEach(i -> names.put(i.getOrderItemId(), i.getProductName()));
        // The same step recorded for several items at the same moment (one parcel) is shown once.
        Map<String, List<String>> products = new java.util.LinkedHashMap<>();
        Map<String, TrackingLine> lines = new java.util.LinkedHashMap<>();
        eventRepository.findByOrderItemOrderItemIdInOrderByCreatedAtAscEventIdAsc(names.keySet()).forEach(e -> {
            LocalDateTime at = e.getCreatedAt();
            String key = e.getStatus() + "|" + e.getDescription() + "|"
                    + (at == null ? "" : at.truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
            products.computeIfAbsent(key, k -> new ArrayList<>()).add(names.get(e.getOrderItem().getOrderItemId()));
            // Re-inserting moves a shared step after the last item that reached it, keeping the timeline in order.
            TrackingLine line = lines.remove(key);
            lines.put(key, line != null ? line : new TrackingLine(null, e.getStatus(), e.getDescription(), at));
        });
        List<TrackingLine> history = new ArrayList<>();
        history.add(new TrackingLine(null, OrderStatus.PLACED, "Order placed", order.getCreatedAt()));
        lines.forEach((key, line) -> {
            List<String> forItems = products.get(key).stream().distinct().toList();
            String product = items.size() > 1 && forItems.size() == items.size() ? null : String.join(", ", forItems);
            history.add(new TrackingLine(product, line.status(), line.description(), line.at()));
        });
        return new TrackingResponse(shipment != null && shipment.getTrackingNumber() != null ? shipment.getTrackingNumber() : number,
                shipment == null ? null : shipment.getCourierService(),
                shipment == null ? ShippingStatus.fromOrder(order.getStatus(), null) : shipment.getShippingStatus(),
                shipment == null ? ShippingMethod.STANDARD : shipment.getShippingMethod(), order.getOrderId(),
                order.getOrderNumber(), order.getCity(), order.getPincode(),
                shipment == null ? null : shipment.getEstimatedDelivery(),
                shipment == null ? null : shipment.getShippedAt(), shipment == null ? null : shipment.getDeliveredAt(),
                items.stream().map(i -> new TrackedItem(i.getProductName(), i.getImageUrl(), i.getQuantity(), i.getItemStatus())).toList(),
                history);
    }

    // ---------------------------------------------------------------- helpers

    private Shipment findShipment(Long orderId) {
        return shipmentRepository.findByOrderOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Order " + orderId + " has no shipping record."));
    }

    private void recordEvent(OrderItem item, OrderStatus status, String description) {
        OrderItemEvent event = new OrderItemEvent();
        event.setOrderItem(item);
        event.setStatus(status);
        event.setDescription(description.length() > 255 ? description.substring(0, 255) : description);
        event.setCreatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    static String label(ShippingStatus s) {
        String text = s.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
