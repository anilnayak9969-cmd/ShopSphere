package com.example.ecommerce.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * Operations shared by checkout, fulfilment, cancellation and returns, so stock, refunds and
 * order status are always handled the same way. Must be called inside a transaction.
 */
@Component
@RequiredArgsConstructor
public class OrderSupport {

    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;

    /** Puts items back into stock (row-locked) and lowers their sold counts. */
    public void restock(Collection<OrderItem> items) {
        if (items.isEmpty()) {
            return;
        }
        Map<Long, Product> locked = lock(items);
        for (OrderItem item : items) {
            Product product = locked.get(item.getProduct().getProductId());
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            product.setSoldCount(Math.max(0, product.getSoldCount() - item.getQuantity()));
        }
    }

    /**
     * Deducts stock for every item if — and only if — all of them are available.
     * Returns false (changing nothing) when any item has too little stock.
     */
    public boolean reserveStock(Collection<OrderItem> items) {
        Map<Long, Product> locked = lock(items);
        boolean enough = items.stream()
                .allMatch(i -> locked.get(i.getProduct().getProductId()).getStockQuantity() >= i.getQuantity());
        if (!enough) {
            return false;
        }
        for (OrderItem item : items) {
            Product product = locked.get(item.getProduct().getProductId());
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            product.setSoldCount(product.getSoldCount() + item.getQuantity());
        }
        return true;
    }

    /** What the customer actually paid for one line: its total minus its share of the coupon discount. */
    public BigDecimal refundShare(CustomerOrder order, OrderItem item) {
        return PricingPolicy.refundShare(item.getLineTotal(), order.getSubtotal(), order.getDiscountAmount());
    }

    /** Recomputes the order status from its items and handles "whole order delivered" side effects. */
    public void refreshOrderStatus(CustomerOrder order) {
        List<OrderStatus> statuses = order.getItems().stream().map(OrderItem::getItemStatus).toList();
        OrderStatus derived = OrderStatus.deriveOrderStatus(statuses);
        order.setStatus(derived);
        if (derived == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            order.setDeliveredAt(LocalDateTime.now());
            // Cash on Delivery is collected when the last item is delivered.
            if (order.getPaymentMethod() == PaymentMethod.COD) {
                Payment payment = paymentRepository.findTopByOrderOrderIdOrderByCreatedAtDesc(order.getOrderId())
                        .orElse(null);
                if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.SUCCESS);
                    order.setPaidAt(LocalDateTime.now());
                }
            }
        }
    }

    private Map<Long, Product> lock(Collection<OrderItem> items) {
        List<Long> ids = items.stream().map(i -> i.getProduct().getProductId()).distinct().toList();
        return productRepository.lockByIds(ids).stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));
    }
}
