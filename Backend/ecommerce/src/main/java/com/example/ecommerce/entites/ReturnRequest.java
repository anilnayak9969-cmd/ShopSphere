package com.example.ecommerce.entites;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.ecommerce.enums.ReturnStatus;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A customer's request to return one delivered order item. One request per item. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "return_requests", indexes = {
        @Index(name = "idx_returns_customer", columnList = "customer_id"),
        @Index(name = "idx_returns_seller", columnList = "seller_id")})
public class ReturnRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id")
    private Long returnId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /** Short code such as DAMAGED, WRONG_ITEM, NOT_AS_DESCRIBED, NO_LONGER_NEEDED, OTHER. */
    @Column(nullable = false, length = 30)
    private String reason;

    @Column(length = 1000)
    private String comments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status;

    /** Amount refunded to the customer for this item (after its share of any coupon discount). */
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "resolution_note", length = 255)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
