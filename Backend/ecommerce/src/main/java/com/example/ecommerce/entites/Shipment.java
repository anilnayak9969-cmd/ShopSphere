package com.example.ecommerce.entites;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.ecommerce.enums.DeliveryZone;
import com.example.ecommerce.enums.ShippingMethod;
import com.example.ecommerce.enums.ShippingStatus;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shipping Management: one shipping record per order — courier, tracking number, status, cost and how it
 * was priced (method, zone, weight). Created at checkout and kept in step with the order's items.
 * Each order item also keeps its own courier and tracking number, because items from different sellers
 * can travel separately; this record is the order-level view the shipping dashboard shows.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "shipping", indexes = @Index(name = "idx_shipping_tracking", columnList = "tracking_number"))
public class Shipment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipping_id")
    private Long shippingId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private CustomerOrder order;

    @Column(name = "courier_service", length = 100)
    private String courierService;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_status", nullable = false, length = 50)
    private ShippingStatus shippingStatus = ShippingStatus.PENDING;

    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_method", nullable = false, length = 20)
    private ShippingMethod shippingMethod = ShippingMethod.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_zone", length = 20)
    private DeliveryZone deliveryZone;

    /** Total weight of the parcel(s) used for pricing. */
    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal weightKg = BigDecimal.ZERO;

    @Column(name = "estimated_delivery")
    private LocalDate estimatedDelivery;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
}
