package com.example.ecommerce.entites;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Payment Management: one refund transaction. Every refund — automatic (order cancelled, return
 * approved) or issued by an admin — is logged here, including failed attempts, and shown on the
 * admin payment dashboard. payments.refunded_amount is the running total of the successful ones.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refunds", indexes = @Index(name = "idx_refunds_payment", columnList = "payment_id"))
public class Refund extends AuditableEntity {

    /** RAZORPAY: sent back through the gateway. MANUAL: paid out by the finance team (cash on delivery). */
    public enum Mode { RAZORPAY, MANUAL }

    public enum Status { PROCESSED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long refundId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Mode mode;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** Razorpay's refund id (rfnd_…), when the gateway processed it. */
    @Column(name = "razorpay_refund_id", length = 60)
    private String razorpayRefundId;

    @Column(nullable = false, length = 255)
    private String reason;

    /** "System" for automatic refunds, otherwise the admin's name. */
    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;
}
