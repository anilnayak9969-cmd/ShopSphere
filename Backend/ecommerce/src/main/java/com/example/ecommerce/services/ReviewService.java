package com.example.ecommerce.services;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.review.ReviewEligibility;
import com.example.ecommerce.dtos.review.ReviewRequest;
import com.example.ecommerce.dtos.review.ReviewResponse;
import com.example.ecommerce.dtos.review.ReviewSummary;

public interface ReviewService {

    ReviewSummary summary(Long productId);

    PageResponse<ReviewResponse> productReviews(Long productId, int page, int size);

    ReviewEligibility eligibility(Long userId, Long productId);

    /** Creates the customer's review, or updates it if they already reviewed this product. */
    ReviewResponse saveReview(Long userId, Long productId, ReviewRequest request);

    void deleteOwnReview(Long userId, Long productId);

    PageResponse<ReviewResponse> customerReviews(Long userId, int page, int size);

    PageResponse<ReviewResponse> adminReviews(Integer rating, int page, int size);

    void adminDelete(Long reviewId);
}
