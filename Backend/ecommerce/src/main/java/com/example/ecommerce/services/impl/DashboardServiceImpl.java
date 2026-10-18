package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.admin.AdminDashboardResponse;
import com.example.ecommerce.dtos.admin.SellerDashboardResponse;
import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.order.OrderResponse;
import com.example.ecommerce.dtos.order.SellerOrderItemResponse;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.ApprovalStatus;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.enums.ReturnStatus;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CategoryRepository;
import com.example.ecommerce.repositories.ContactMessageRepository;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.ReturnRequestRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.DashboardService;
import com.example.ecommerce.services.OrderService;
import com.example.ecommerce.services.SettingsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ReturnRequestRepository returnRepository;
    private final ContactMessageRepository contactRepository;
    private final OrderService orderService;
    private final SettingsService settingsService;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse adminDashboard() {
        int lowStock = settingsService.get().getLowStockThreshold();
        List<OrderResponse> recent = orderService.adminOrders(null, null, 0, 10).content().stream()
                .filter(o -> o.status() != OrderStatus.PENDING_PAYMENT).limit(6).toList();
        List<UserResponse> applications = userRepository
                .findTop5ByRoleAndSellerStatusOrderByCreatedAtDesc(Role.SELLER, SellerStatus.PENDING).stream()
                .map(UserResponse::from).toList();
        BigDecimal collected = paymentRepository.sumAmountByStatus(PaymentStatus.SUCCESS)
                .add(paymentRepository.sumAmountByStatus(PaymentStatus.REFUNDED));
        BigDecimal refunded = paymentRepository.sumRefunded();
        return new AdminDashboardResponse(
                userRepository.countByRole(Role.CUSTOMER),
                userRepository.countByRole(Role.SELLER),
                userRepository.countByRoleAndSellerStatus(Role.SELLER, SellerStatus.PENDING),
                categoryRepository.count(),
                categoryRepository.countByStatusTrue(),
                productRepository.count(),
                productRepository.countByActiveTrue(),
                productRepository.countByApprovalStatus(ApprovalStatus.PENDING),
                productRepository.countByActiveTrueAndStockQuantityLessThanEqual(lowStock),
                orderRepository.countByStatusIn(OrderServiceImpl.PAID_STATUSES)
                        + orderRepository.countByStatus(OrderStatus.RETURNED) + orderRepository.countByStatus(OrderStatus.REFUNDED),
                orderRepository.countByStatusIn(EnumSet.of(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PACKED)),
                orderRepository.countByStatus(OrderStatus.DELIVERED),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                returnRepository.countByStatus(ReturnStatus.REQUESTED),
                contactRepository.countByResolvedFalse(),
                collected.subtract(refunded),
                refunded,
                recent,
                applications);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerDashboardResponse sellerDashboard(Long sellerId) {
        User seller = userRepository.findById(sellerId).orElseThrow(() -> ApiException.notFound("Seller not found."));
        int lowStock = settingsService.get().getLowStockThreshold();
        Object[] totals = orderItemRepository.sellerTotals(sellerId, OrderServiceImpl.SOLD_ITEM_STATUSES).get(0);
        List<SellerOrderItemResponse> recent = orderService.sellerOrderItems(sellerId, null, 0, 5).content();
        return new SellerDashboardResponse(
                seller.getSellerStatus(),
                seller.getStoreName(),
                productRepository.countBySellerId(sellerId),
                productRepository.countBySellerIdAndActiveTrue(sellerId),
                productRepository.countBySellerIdAndApprovalStatus(sellerId, ApprovalStatus.PENDING),
                productRepository.countBySellerIdAndStockQuantityLessThanEqual(sellerId, lowStock),
                lowStock,
                orderItemRepository.countSellerItems(sellerId, OrderServiceImpl.OPEN_ITEM_STATUSES),
                orderItemRepository.countSellerItems(sellerId, EnumSet.of(OrderStatus.DELIVERED)),
                returnRepository.countBySellerIdAndStatus(sellerId, ReturnStatus.REQUESTED),
                orderItemRepository.countSellerOrders(sellerId, OrderServiceImpl.SOLD_ITEM_STATUSES),
                ((Number) totals[0]).longValue(),
                totals[1] instanceof BigDecimal revenue ? revenue : new BigDecimal(totals[1].toString()),
                recent);
    }
}
