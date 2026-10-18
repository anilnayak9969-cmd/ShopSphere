package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.returns.ReturnCreateRequest;
import com.example.ecommerce.dtos.returns.ReturnResponse;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.ReturnRequest;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.ReturnStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.ReturnRequestRepository;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OrderSupport;
import com.example.ecommerce.services.RefundService;
import com.example.ecommerce.services.ReturnService;
import com.example.ecommerce.services.SettingsService;

import lombok.RequiredArgsConstructor;

/**
 * Return flow: customer requests (delivered item, within the return window) -> seller or admin
 * approves (item restocked, refund issued for what was paid) or rejects (item stays delivered).
 */
@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderSupport orderSupport;
    private final RefundService refundService;
    private final NotificationService notificationService;
    private final SettingsService settingsService;

    @Override
    @Transactional
    public ReturnResponse requestReturn(Long customerId, ReturnCreateRequest request) {
        OrderItem item = orderItemRepository.findWithOrderByOrderItemId(request.orderItemId())
                .filter(i -> i.getOrder().getCustomer().getId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Order item not found."));
        if (item.getItemStatus() != OrderStatus.DELIVERED) {
            throw ApiException.badRequest("NOT_RETURNABLE", "Only delivered items can be returned.");
        }
        int window = settingsService.get().getReturnWindowDays();
        if (item.getDeliveredAt() == null || LocalDateTime.now().isAfter(item.getDeliveredAt().plusDays(window))) {
            throw ApiException.badRequest("RETURN_WINDOW_CLOSED",
                    "The " + window + "-day return window for this item has closed.");
        }
        if (returnRepository.existsByOrderItemOrderItemId(item.getOrderItemId())) {
            throw ApiException.conflict("RETURN_EXISTS", "A return has already been requested for this item.");
        }

        ReturnRequest request1 = new ReturnRequest();
        request1.setOrderItem(item);
        request1.setCustomer(item.getOrder().getCustomer());
        request1.setSeller(item.getSeller());
        request1.setReason(request.reason());
        request1.setComments(request.comments() == null || request.comments().isBlank() ? null : request.comments().trim());
        request1.setStatus(ReturnStatus.REQUESTED);
        returnRepository.save(request1);

        item.setItemStatus(OrderStatus.RETURN_REQUESTED);
        orderSupport.refreshOrderStatus(item.getOrder());
        notificationService.notify(item.getSeller(), "Return requested",
                "A customer wants to return " + item.getProductName() + " (order " + item.getOrder().getOrderNumber() + ").",
                "/seller/returns");
        return ReturnResponse.from(request1);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> customerReturns(Long customerId, int page, int size) {
        return PageResponse.of(returnRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable(page, size)),
                ReturnResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> sellerReturns(Long sellerId, ReturnStatus status, int page, int size) {
        return PageResponse.of(returnRepository.sellerReturns(sellerId, status, pageable(page, size)), ReturnResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> adminReturns(ReturnStatus status, int page, int size) {
        return PageResponse.of(returnRepository.adminSearch(status, pageable(page, size)), ReturnResponse::from);
    }

    @Override
    @Transactional
    public ReturnResponse decide(Long sellerId, Long returnId, boolean approve, String note) {
        ReturnRequest request = returnRepository.findWithDetailsByReturnId(returnId)
                .filter(r -> sellerId == null || r.getSeller().getId().equals(sellerId))
                .orElseThrow(() -> ApiException.notFound("Return request not found."));
        if (request.getStatus() != ReturnStatus.REQUESTED) {
            throw ApiException.conflict("ALREADY_DECIDED", "This return has already been " +
                    request.getStatus().name().toLowerCase() + ".");
        }
        OrderItem item = request.getOrderItem();
        CustomerOrder order = item.getOrder();
        String cleanNote = note == null || note.isBlank() ? null : note.trim();

        if (approve) {
            orderSupport.restock(List.of(item));
            item.setItemStatus(OrderStatus.RETURNED);
            BigDecimal amount = orderSupport.refundShare(order, item);
            RefundService.Outcome outcome = refundService.refund(order, amount, false);
            request.setRefundAmount(amount);
            request.setStatus(outcome == RefundService.Outcome.FAILED ? ReturnStatus.APPROVED : ReturnStatus.REFUNDED);
            if (outcome != RefundService.Outcome.FAILED) {
                item.setItemStatus(OrderStatus.REFUNDED);
            }
            request.setResolutionNote(cleanNote != null ? cleanNote : "Return approved" + OrderServiceImpl.refundNote(outcome));
        } else {
            item.setItemStatus(OrderStatus.DELIVERED);
            request.setStatus(ReturnStatus.REJECTED);
            request.setResolutionNote(cleanNote != null ? cleanNote : "Return rejected");
        }
        request.setResolvedAt(LocalDateTime.now());
        orderSupport.refreshOrderStatus(order);
        notificationService.notify(request.getCustomer(), approve ? "Return approved" : "Return rejected",
                item.getProductName() + ": " + request.getResolutionNote(), "/account/returns");
        return ReturnResponse.from(request);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }
}
