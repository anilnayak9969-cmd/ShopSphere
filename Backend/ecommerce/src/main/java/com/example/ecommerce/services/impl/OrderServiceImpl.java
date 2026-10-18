package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.delivery.DeliveryDashboardResponse;
import com.example.ecommerce.dtos.delivery.DeliveryJobResponse;
import com.example.ecommerce.dtos.delivery.DeliveryPartnerResponse;
import com.example.ecommerce.entites.DeliveryPartnerProfile;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.dtos.order.TrackingEvent;
import com.example.ecommerce.entites.OrderItemEvent;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.repositories.DeliveryPartnerProfileRepository;
import com.example.ecommerce.repositories.OrderItemEventRepository;
import com.example.ecommerce.services.LiveEventService;
import com.example.ecommerce.repositories.SellerProfileRepository;
import com.example.ecommerce.dtos.order.FulfilmentUpdateRequest;
import com.example.ecommerce.dtos.order.OrderItemResponse;
import com.example.ecommerce.dtos.order.OrderResponse;
import com.example.ecommerce.dtos.order.OrderResponse.PaymentInfo;
import com.example.ecommerce.dtos.order.PaymentResponse;
import com.example.ecommerce.dtos.order.SellerOrderItemResponse;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.ReturnRequest;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.ReturnRequestRepository;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OrderService;
import com.example.ecommerce.services.OrderSupport;
import com.example.ecommerce.services.RefundService;
import com.example.ecommerce.services.SettingsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** Order statuses that mean "the order was placed" (paid online or confirmed as COD). */
    public static final Set<OrderStatus> PAID_STATUSES = EnumSet.of(OrderStatus.PLACED, OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING, OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED, OrderStatus.CANCELLATION_REQUESTED, OrderStatus.RETURN_REQUESTED);

    /** Item statuses that still count as a sale (used for revenue and units sold). */
    public static final Set<OrderStatus> SOLD_ITEM_STATUSES = EnumSet.of(OrderStatus.PLACED, OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING, OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED);

    /** Item statuses a seller still has work to do on. */
    public static final Set<OrderStatus> OPEN_ITEM_STATUSES = EnumSet.of(OrderStatus.PLACED, OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING, OrderStatus.PACKED);

    /** Item statuses that prove the customer received the product (eligible to review). */
    public static final Set<OrderStatus> RECEIVED_ITEM_STATUSES =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED);

    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE = EnumSet.of(OrderStatus.PLACED, OrderStatus.CONFIRMED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ReturnRequestRepository returnRepository;
    private final OrderSupport orderSupport;
    private final RefundService refundService;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final SellerProfileRepository sellerProfileRepository;
    private final DeliveryPartnerProfileRepository deliveryPartnerProfileRepository;
    private final OrderItemEventRepository orderItemEventRepository;
    private final LiveEventService liveEventService;

    // --------------------------------------------------------------- customer

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> customerOrders(Long customerId, int page, int size) {
        // Unpaid online checkout attempts are hidden from the customer's history.
        return PageResponse.of(orderRepository.findByCustomerIdAndStatusNotOrderByCreatedAtDesc(customerId,
                OrderStatus.PENDING_PAYMENT, pageable(page, size)), o -> OrderResponse.summary(o, paymentInfo(o)));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse customerOrder(Long customerId, Long orderId) {
        return withReturnInfo(findCustomerOrder(customerId, orderId));
    }

    @Override
    @Transactional
    public OrderResponse cancelByCustomer(Long customerId, Long orderId) {
        CustomerOrder order = findCustomerOrder(customerId, orderId);
        List<OrderItem> active = order.getItems().stream().filter(i -> i.getItemStatus().isActiveItem()).toList();
        if (active.isEmpty() || !active.stream().allMatch(i -> CUSTOMER_CANCELLABLE.contains(i.getItemStatus()))) {
            throw ApiException.badRequest("NOT_CANCELLABLE",
                    "This order is already being packed or shipped, so it can no longer be cancelled online.");
        }
        cancelItems(order, active, "Cancelled by customer");
        active.forEach(item -> notificationService.notify(item.getSeller(), "Order item cancelled",
                item.getProductName() + " in order " + order.getOrderNumber() + " was cancelled by the customer.",
                "/seller/orders"));
        return withReturnInfo(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> customerPayments(Long customerId, int page, int size) {
        return PageResponse.of(paymentRepository.customerPayments(customerId, pageable(page, size)),
                p -> PaymentResponse.from(p, false));
    }

    // ----------------------------------------------------------------- seller

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SellerOrderItemResponse> sellerOrderItems(Long sellerId, OrderStatus status, int page, int size) {
        return PageResponse.of(orderItemRepository.findSellerItems(sellerId, status, pageable(page, size)),
                SellerOrderItemResponse::from);
    }

    @Override
    @Transactional
    public SellerOrderItemResponse updateSellerItem(Long sellerId, Long orderItemId, FulfilmentUpdateRequest request) {
        OrderItem item = orderItemRepository.findByOrderItemIdAndSellerId(orderItemId, sellerId)
                .filter(i -> i.getOrder().getStatus() != OrderStatus.PENDING_PAYMENT)
                .orElseThrow(() -> ApiException.notFound("Order item not found."));
        if (item.getDeliveryPartner() != null) {
            throw ApiException.conflict("DELIVERY_PARTNER_ASSIGNED",
                    "A ShopSphere delivery partner has picked up this parcel and will update its status.");
        }
        if (!OrderStatus.SELLER_TARGETS.contains(request.status())) {
            throw ApiException.forbidden("SELLER_STEP_NOT_ALLOWED",
                    "Sellers confirm and pack orders. Shipping and delivery are recorded by the ShopSphere delivery partner.");
        }
        applyItemStatus(item, request.status(), request.courierName(), request.trackingNumber(), request.note(), true);
        return SellerOrderItemResponse.from(item);
    }

    // ------------------------------------------------------------------ delivery partners

    private static final Set<OrderStatus> ACTIVE_DELIVERY = EnumSet.of(OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY);
    private static final Set<OrderStatus> COMPLETED_DELIVERY = EnumSet.of(OrderStatus.DELIVERED,
            OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED, OrderStatus.REFUNDED);
    private static final String COURIER = "ShopSphere Delivery";

    @Override
    @Transactional(readOnly = true)
    public DeliveryDashboardResponse partnerDashboard(Long partnerId) {
        DeliveryPartnerProfile profile = partnerProfile(partnerId);
        return new DeliveryDashboardResponse(DeliveryPartnerResponse.from(profile),
                orderItemRepository.countAvailablePickups(),
                orderItemRepository.countPartnerItems(partnerId, ACTIVE_DELIVERY),
                orderItemRepository.countPartnerDeliveredSince(partnerId, java.time.LocalDate.now().atStartOfDay()),
                orderItemRepository.countPartnerItems(partnerId, COMPLETED_DELIVERY));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeliveryJobResponse> partnerPickups(Long partnerId, int page, int size) {
        String city = partnerProfile(partnerId).getServiceCity();
        return PageResponse.of(orderItemRepository.findAvailablePickups(city, pageable(page, size)),
                item -> job(item, false, city));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeliveryJobResponse> partnerJobs(Long partnerId, boolean completed, int page, int size) {
        String city = partnerProfile(partnerId).getServiceCity();
        return PageResponse.of(orderItemRepository.findPartnerItems(partnerId,
                completed ? COMPLETED_DELIVERY : ACTIVE_DELIVERY, pageable(page, size)), item -> job(item, true, city));
    }

    @Override
    @Transactional
    public DeliveryJobResponse acceptPickup(Long partnerId, Long orderItemId) {
        DeliveryPartnerProfile profile = partnerProfile(partnerId);
        User partner = profile.getUser();
        if (partner.getSellerStatus() != SellerStatus.APPROVED || !partner.isActive()) {
            throw ApiException.forbidden("PARTNER_NOT_APPROVED",
                    "Your delivery partner application must be approved before you can accept pickups.");
        }
        String partnerName = partner.getFullName();
        String city = profile.getServiceCity();
        // Atomic: if two partners tap "Accept" together, only one update succeeds.
        if (orderItemRepository.claimPickup(orderItemId, partner, LocalDateTime.now()) == 0) {
            throw ApiException.conflict("PICKUP_UNAVAILABLE",
                    "This parcel is no longer available — another partner may have accepted it.");
        }
        OrderItem item = orderItemRepository.findWithOrderByOrderItemId(orderItemId)
                .orElseThrow(() -> ApiException.notFound("Order item not found."));
        applyItemStatus(item, OrderStatus.SHIPPED, COURIER, "SSD-" + item.getOrderItemId(), null, false);
        notificationService.notify(item.getSeller(), "Parcel picked up",
                item.getProductName() + " (order " + item.getOrder().getOrderNumber() + ") was accepted by "
                        + partnerName + " of ShopSphere Delivery.", "/seller/orders");
        return job(item, true, city);
    }

    @Override
    @Transactional
    public DeliveryJobResponse updatePartnerJob(Long partnerId, Long orderItemId, OrderStatus status) {
        String city = partnerProfile(partnerId).getServiceCity();
        OrderItem item = orderItemRepository.findWithOrderByOrderItemId(orderItemId)
                .filter(i -> i.getDeliveryPartner() != null && i.getDeliveryPartner().getId().equals(partnerId))
                .orElseThrow(() -> ApiException.notFound("Delivery not found."));
        boolean allowed = (item.getItemStatus() == OrderStatus.SHIPPED && status == OrderStatus.OUT_FOR_DELIVERY)
                || (item.getItemStatus() == OrderStatus.OUT_FOR_DELIVERY && status == OrderStatus.DELIVERED);
        if (!allowed) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    "This parcel cannot move from " + item.getItemStatus() + " to " + status + ".");
        }
        applyItemStatus(item, status, null, null, null, false);
        return job(item, true, city);
    }

    private DeliveryPartnerProfile partnerProfile(Long partnerId) {
        return deliveryPartnerProfileRepository.findByUserId(partnerId)
                .orElseThrow(() -> ApiException.notFound("Delivery partner profile not found."));
    }

    private DeliveryJobResponse job(OrderItem item, boolean assigned, String partnerCity) {
        return DeliveryJobResponse.from(item,
                sellerProfileRepository.findByUserId(item.getSeller().getId()).orElse(null), assigned, partnerCity);
    }

    // ------------------------------------------------------------------ admin

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> adminOrders(OrderStatus status, String search, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.of(orderRepository.search(status, term, pageable(page, size)),
                o -> OrderResponse.summary(o, paymentInfo(o)));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse adminOrder(Long orderId) {
        return withReturnInfo(findOrder(orderId));
    }

    @Override
    @Transactional
    public OrderResponse adminUpdateStatus(Long orderId, OrderStatus status, String note) {
        CustomerOrder order = findOrder(orderId);
        if (status == OrderStatus.CANCELLED) {
            List<OrderItem> cancellable = order.getItems().stream()
                    .filter(i -> i.getItemStatus().isActiveItem() && i.getItemStatus().canTransitionTo(OrderStatus.CANCELLED))
                    .toList();
            if (cancellable.isEmpty()) {
                throw ApiException.badRequest("INVALID_STATUS_TRANSITION", "No item of this order can be cancelled any more.");
            }
            cancelItems(order, cancellable, note == null || note.isBlank() ? "Cancelled by ShopSphere" : note.trim());
            return withReturnInfo(order);
        }
        if (!OrderStatus.FULFILMENT_TARGETS.contains(status)) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION", "Orders cannot be set to " + status + " here.");
        }
        List<OrderItem> eligible = order.getItems().stream()
                .filter(i -> i.getItemStatus().canTransitionTo(status)).toList();
        if (eligible.isEmpty()) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    "No item of this order can move from its current status to " + status + ".");
        }
        // The admin may override missing tracking details when resolving an issue.
        eligible.forEach(item -> applyItemStatus(item, status, null, null, note, false));
        return withReturnInfo(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> adminPayments(PaymentStatus status, boolean refundedOnly, int page, int size) {
        return PageResponse.of(paymentRepository.adminSearch(status, refundedOnly, pageable(page, size)),
                p -> PaymentResponse.from(p, true));
    }

    // ---------------------------------------------------------------- helpers

    /** Validates and applies one item status change, with the side effects of that status. */
    private void applyItemStatus(OrderItem item, OrderStatus target, String courier, String tracking, String note,
                                 boolean requireTracking) {
        OrderStatus current = item.getItemStatus();
        if (!OrderStatus.FULFILMENT_TARGETS.contains(target) || !current.canTransitionTo(target)) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    "This item cannot move from " + current + " to " + target + ".");
        }
        CustomerOrder order = item.getOrder();
        if (target == OrderStatus.CANCELLED) {
            cancelItems(order, List.of(item), note == null || note.isBlank() ? "Cancelled by the seller" : note.trim());
            return;
        }
        if (target == OrderStatus.SHIPPED) {
            if (requireTracking && (isBlank(courier) || isBlank(tracking))) {
                throw ApiException.badRequest("TRACKING_REQUIRED", "Enter the courier name and tracking number to ship.");
            }
            item.setCourierName(isBlank(courier) ? item.getCourierName() : courier.trim());
            item.setTrackingNumber(isBlank(tracking) ? item.getTrackingNumber() : tracking.trim());
            item.setShippedAt(LocalDateTime.now());
        }
        if (target == OrderStatus.DELIVERED) {
            item.setDeliveredAt(LocalDateTime.now());
        }
        boolean pickupsChanged = current == OrderStatus.PACKED || target == OrderStatus.PACKED;
        item.setItemStatus(target);
        recordEvent(item, target, trackingText(item, target));
        if (pickupsChanged) {
            liveEventService.toRole(Role.DELIVERY_PARTNER, "pickups", Map.of("orderItemId", item.getOrderItemId()));
        }
        orderSupport.refreshOrderStatus(order);
        notificationService.notify(order.getCustomer(), statusTitle(target),
                item.getProductName() + " (order " + order.getOrderNumber() + ") is now " + label(target)
                        + (target == OrderStatus.SHIPPED && item.getTrackingNumber() != null
                        ? " — " + item.getCourierName() + " tracking " + item.getTrackingNumber() : "") + ".",
                "/account/orders/" + order.getOrderId());
    }

    /** Cancels lines: puts stock back, refunds what the customer paid for them, updates the order. */
    private void cancelItems(CustomerOrder order, List<OrderItem> items, String note) {
        orderSupport.restock(items);
        BigDecimal refund = items.stream().map(i -> orderSupport.refundShare(order, i))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean pickupsChanged = items.stream().anyMatch(i -> i.getItemStatus() == OrderStatus.PACKED);
        items.forEach(i -> {
            i.setItemStatus(OrderStatus.CANCELLED);
            recordEvent(i, OrderStatus.CANCELLED, note);
        });
        if (pickupsChanged) {
            liveEventService.toRole(Role.DELIVERY_PARTNER, "pickups", Map.of("orderId", order.getOrderId()));
        }
        boolean wholeOrder = order.getItems().stream().noneMatch(i -> i.getItemStatus().isActiveItem())
                && order.getItems().stream().allMatch(i -> i.getItemStatus() == OrderStatus.CANCELLED);
        if (wholeOrder) {
            refund = order.getTotalAmount(); // includes shipping when nothing is delivered at all
        }
        RefundService.Outcome outcome = refundService.refund(order, refund, wholeOrder);
        order.setStatusNote(note + refundNote(outcome));
        orderSupport.refreshOrderStatus(order);
        notificationService.notify(order.getCustomer(), wholeOrder ? "Order cancelled" : "Item cancelled",
                (wholeOrder ? "Order " + order.getOrderNumber() : items.size() + " item(s) of order " + order.getOrderNumber())
                        + " cancelled. " + note + refundNote(outcome), "/account/orders/" + order.getOrderId());
    }

    private void recordEvent(OrderItem item, OrderStatus status, String description) {
        OrderItemEvent event = new OrderItemEvent();
        event.setOrderItem(item);
        event.setStatus(status);
        event.setDescription(description == null || description.isBlank() ? label(status)
                : description.length() > 255 ? description.substring(0, 255) : description);
        event.setCreatedAt(LocalDateTime.now());
        orderItemEventRepository.save(event);
    }

    /** Plain-words tracking line for a status change, naming who did it. */
    private static String trackingText(OrderItem item, OrderStatus status) {
        String partner = item.getDeliveryPartner() == null ? null : item.getDeliveryPartner().getFullName().split(" ")[0];
        return switch (status) {
            case CONFIRMED -> "Confirmed by " + item.getStoreName();
            case PACKED -> "Packed by " + item.getStoreName() + " — ready for pickup";
            case SHIPPED -> partner != null
                    ? "Picked up from " + item.getStoreName() + " by " + partner + " (ShopSphere Delivery)"
                    : "Shipped via " + item.getCourierName() + " · tracking " + item.getTrackingNumber();
            case OUT_FOR_DELIVERY -> "Out for delivery" + (partner != null ? " with " + partner : "");
            case DELIVERED -> "Delivered" + (partner != null ? " by " + partner : "");
            default -> label(status);
        };
    }

    static String refundNote(RefundService.Outcome outcome) {
        return switch (outcome) {
            case REFUNDED -> " — the refund has been issued to your original payment method.";
            case MANUAL -> " — the refund will be processed manually by our team.";
            case FAILED -> " — the refund could not be issued automatically; our team will process it.";
            case NOT_REQUIRED -> ".";
        };
    }

    private OrderResponse withReturnInfo(CustomerOrder order) {
        List<Long> itemIds = order.getItems().stream().map(OrderItem::getOrderItemId).toList();
        Map<Long, ReturnRequest> returns = itemIds.isEmpty() ? Map.of()
                : returnRepository.findByOrderItemOrderItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(r -> r.getOrderItem().getOrderItemId(), Function.identity()));
        int window = settingsService.get().getReturnWindowDays();
        Map<Long, List<TrackingEvent>> tracking = itemIds.isEmpty() ? Map.of()
                : orderItemEventRepository.findByOrderItemOrderItemIdInOrderByCreatedAtAscEventIdAsc(itemIds).stream()
                .collect(Collectors.groupingBy(e -> e.getOrderItem().getOrderItemId(),
                        Collectors.mapping(TrackingEvent::from, Collectors.toList())));
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> OrderItemResponse.from(i, returns.get(i.getOrderItemId()), window,
                        tracking.getOrDefault(i.getOrderItemId(), List.of()))).toList();
        return OrderResponse.full(order, paymentInfo(order), items);
    }

    private PaymentInfo paymentInfo(CustomerOrder order) {
        return paymentRepository.findTopByOrderOrderIdOrderByCreatedAtDesc(order.getOrderId())
                .map(p -> new PaymentInfo(p.getStatus(), p.getRefundedAmount())).orElse(PaymentInfo.NONE);
    }

    private CustomerOrder findCustomerOrder(Long customerId, Long orderId) {
        return orderRepository.findByOrderIdAndCustomerId(orderId, customerId)
                .filter(o -> o.getStatus() != OrderStatus.PENDING_PAYMENT)
                .orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    private CustomerOrder findOrder(Long orderId) {
        return orderRepository.findWithItemsByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Order " + orderId + " was not found."));
    }

    private static String statusTitle(OrderStatus s) {
        return switch (s) {
            case CONFIRMED -> "Order confirmed";
            case PACKED -> "Order packed";
            case SHIPPED -> "Order shipped";
            case OUT_FOR_DELIVERY -> "Out for delivery";
            case DELIVERED -> "Delivered";
            default -> "Order update";
        };
    }

    private static String label(OrderStatus s) {
        return s.name().toLowerCase().replace('_', ' ');
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }
}
