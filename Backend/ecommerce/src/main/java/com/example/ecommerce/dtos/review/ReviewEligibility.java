package com.example.ecommerce.dtos.review;

/** Tells the product page whether to show the "Write a review" form, and pre-fills it if one exists. */
public record ReviewEligibility(boolean canReview, String reason, ReviewResponse existingReview) {
}
