package com.example.ecommerce.entites;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.ecommerce.enums.OrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of an order. Name, image and price are snapshots taken at checkout, so editing or
 * repricing the product later does not change what the customer actually paid.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_items", indexes = @Index(name = "idx_order_items_seller", columnList = "seller_id"))
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "store_name", length = 120)
    private String storeName;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    /**
     * Fulfilment status of this line. Each seller moves only their own lines
     * (CONFIRMED -> PACKED -> SHIPPED -> DELIVERED); the order status is derived from all lines.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 30,
            columnDefinition = "varchar(30) not null default 'PLACED'")
    private OrderStatus itemStatus = OrderStatus.PENDING_PAYMENT;

    @Column(name = "courier_name", length = 60)
    private String courierName;

    @Column(name = "tracking_number", length = 60)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** ShopSphere delivery partner who accepted the pickup (null until a partner accepts it). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_partner_id")
    private User deliveryPartner;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
}
