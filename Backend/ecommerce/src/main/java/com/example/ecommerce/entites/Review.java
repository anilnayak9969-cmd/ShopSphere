package com.example.ecommerce.entites;

import java.time.LocalDateTime;

import com.example.ecommerce.enums.ReviewStatus;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A customer's rating (1-5) and review. One review per customer per product; only buyers may review. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_reviews_product_user", columnNames = {"product_id", "user_id"}),
        indexes = @Index(name = "idx_reviews_product", columnList = "product_id"))
public class Review extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int rating;

    @Column(length = 120)
    private String title;

    /** The review text (assignment column review_text). */
    @Column(length = 2000)
    private String comment;

    /** Moderation state. Only APPROVED reviews appear on the product page and count towards its rating. */
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", length = 20)
    private ReviewStatus moderationStatus = ReviewStatus.PENDING;

    /** Assignment column: true for approved reviews, false for unapproved (pending or rejected). */
    @Column(name = "status")
    private Boolean approved = Boolean.FALSE;

    /** Why an admin rejected the review; shown to the customer. */
    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    /** Sets the moderation state and keeps the Boolean status column in step with it. */
    public void moderate(ReviewStatus status, String reason) {
        this.moderationStatus = status;
        this.approved = status == ReviewStatus.APPROVED;
        this.rejectionReason = status == ReviewStatus.REJECTED ? reason : null;
        this.moderatedAt = status == ReviewStatus.PENDING ? null : LocalDateTime.now();
    }
}
