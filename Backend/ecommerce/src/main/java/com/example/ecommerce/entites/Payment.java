package com.example.ecommerce.entites;

import java.math.BigDecimal;

import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Razorpay payment attempt for an order. Only provider references are stored —
 * never card numbers, CVVs or UPI PINs.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_rzp_order", columnNames = "razorpay_order_id"))
public class Payment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    /** Null for Cash on Delivery payments. */
    @Column(name = "razorpay_order_id", length = 60)
    private String razorpayOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) not null default 'RAZORPAY'")
    private PaymentMethod method = PaymentMethod.RAZORPAY;

    /** Total refunded so far (partial refunds for single returned or cancelled items add up here). */
    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2,
            columnDefinition = "decimal(12,2) not null default 0")
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "razorpay_payment_id", length = 60)
    private String razorpayPaymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;
}
