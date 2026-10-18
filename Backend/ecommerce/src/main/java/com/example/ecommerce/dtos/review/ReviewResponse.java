package com.example.ecommerce.dtos.review;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.Review;

/** Only the reviewer's first name is shown publicly — never their email. */
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
        LocalDateTime updatedAt) {

    public static ReviewResponse from(Review r) {
        String name = r.getUser().getFullName();
        String firstName = name == null ? "Customer" : name.trim().split("\\s+")[0];
        return new ReviewResponse(r.getReviewId(), r.getProduct().getProductId(), r.getProduct().getProductName(),
                r.getRating(), r.getTitle(), r.getComment(), firstName, true, r.getCreatedAt(), r.getUpdatedAt());
    }
}
