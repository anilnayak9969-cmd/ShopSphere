package com.example.ecommerce.dtos.review;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.Review;
import com.example.ecommerce.enums.ReviewStatus;

/**
 * A review. Publicly only the reviewer's first name is shown — never their email; customerName and
 * customerEmail are filled in for the admin moderation queue only.
 */
public record ReviewResponse(
        Long reviewId,
        Long productId,
        String productName,
        int rating,
        String title,
        String comment,
        String reviewerName,
        boolean verifiedPurchase,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ReviewStatus status,
        boolean approved,
        String rejectionReason,
        LocalDateTime moderatedAt,
        String productImage,
        String customerName,
        String customerEmail) {

    public static ReviewResponse from(Review r) {
        return build(r, false);
    }

    public static ReviewResponse forAdmin(Review r) {
        return build(r, true);
    }

    private static ReviewResponse build(Review r, boolean admin) {
        String name = r.getUser().getFullName();
        String firstName = name == null ? "Customer" : name.trim().split("\\s+")[0];
        ReviewStatus status = r.getModerationStatus() == null ? ReviewStatus.PENDING : r.getModerationStatus();
        return new ReviewResponse(r.getReviewId(), r.getProduct().getProductId(), r.getProduct().getProductName(),
                r.getRating(), r.getTitle(), r.getComment(), firstName, true, r.getCreatedAt(), r.getUpdatedAt(),
                status, status == ReviewStatus.APPROVED, r.getRejectionReason(), r.getModeratedAt(),
                r.getProduct().getImageUrl(), admin ? name : null, admin ? r.getUser().getEmail() : null);
    }
}
