package com.example.ecommerce.services;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.delivery.DeliveryDashboardResponse;
import com.example.ecommerce.dtos.delivery.DeliveryJobResponse;
import com.example.ecommerce.dtos.order.FulfilmentUpdateRequest;
import com.example.ecommerce.dtos.order.OrderResponse;
import com.example.ecommerce.dtos.order.PaymentResponse;
import com.example.ecommerce.dtos.order.SellerOrderItemResponse;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentStatus;

public interface OrderService {

    PageResponse<OrderResponse> customerOrders(Long customerId, int page, int size);

    OrderResponse customerOrder(Long customerId, Long orderId);

    /** Customers may cancel until any item of the order is packed. */
    OrderResponse cancelByCustomer(Long customerId, Long orderId);

    PageResponse<PaymentResponse> customerPayments(Long customerId, int page, int size);

    PageResponse<SellerOrderItemResponse> sellerOrderItems(Long sellerId, OrderStatus status, int page, int size);

    /** A seller moves one of their own order lines forward (confirm, pack, ship, deliver) or cancels it. */
    SellerOrderItemResponse updateSellerItem(Long sellerId, Long orderItemId, FulfilmentUpdateRequest request);

    PageResponse<OrderResponse> adminOrders(OrderStatus status, String search, int page, int size);

    OrderResponse adminOrder(Long orderId);

    /** Admin moves every eligible line of the order to the given status (to resolve stuck orders). */
    OrderResponse adminUpdateStatus(Long orderId, OrderStatus status, String note);

    PageResponse<PaymentResponse> adminPayments(PaymentStatus status, boolean refundedOnly, int page, int size);

    // ---- delivery partners: packed parcels → accepted (shipped) → out for delivery → delivered

    DeliveryDashboardResponse partnerDashboard(Long partnerId);

    /** Packed parcels nobody has accepted yet, deliveries in the partner's city first. */
    PageResponse<DeliveryJobResponse> partnerPickups(Long partnerId, int page, int size);

    /** completed=false: parcels on the way; completed=true: delivered history. */
    PageResponse<DeliveryJobResponse> partnerJobs(Long partnerId, boolean completed, int page, int size);

    DeliveryJobResponse acceptPickup(Long partnerId, Long orderItemId);

    DeliveryJobResponse updatePartnerJob(Long partnerId, Long orderItemId, OrderStatus status);
}
