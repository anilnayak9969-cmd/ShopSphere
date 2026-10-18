package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.review.ReviewEligibility;
import com.example.ecommerce.dtos.review.ReviewRequest;
import com.example.ecommerce.dtos.review.ReviewResponse;
import com.example.ecommerce.dtos.review.ReviewSummary;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.Review;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.ReviewRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.ReviewService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ReviewSummary summary(Long productId) {
        Product product = findProduct(productId);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0L);
        }
        for (Object[] row : reviewRepository.ratingDistribution(productId)) {
            distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return new ReviewSummary(product.getRatingAverage(), product.getRatingCount(), distribution);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> productReviews(Long productId, int page, int size) {
        findProduct(productId);
        return PageResponse.of(reviewRepository.findByProductProductIdOrderByCreatedAtDesc(productId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 20))), ReviewResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewEligibility eligibility(Long userId, Long productId) {
        findProduct(productId);
        ReviewResponse existing = reviewRepository.findByProductProductIdAndUserId(productId, userId)
                .map(ReviewResponse::from).orElse(null);
        if (existing != null) {
            return new ReviewEligibility(true, null, existing);
        }
        if (!orderItemRepository.hasPurchased(userId, productId, OrderServiceImpl.RECEIVED_ITEM_STATUSES)) {
            return new ReviewEligibility(false, "You can review this product once it has been delivered to you.", null);
        }
        return new ReviewEligibility(true, null, null);
    }

    @Override
    @Transactional
    public ReviewResponse saveReview(Long userId, Long productId, ReviewRequest request) {
        Product product = findProduct(productId);
        Review review = reviewRepository.findByProductProductIdAndUserId(productId, userId).orElse(null);
        if (review == null) {
            if (!orderItemRepository.hasPurchased(userId, productId, OrderServiceImpl.RECEIVED_ITEM_STATUSES)) {
                throw ApiException.forbidden("NOT_PURCHASED", "You can review this product once it has been delivered to you.");
            }
            review = new Review();
            review.setProduct(product);
            review.setUser(userRepository.getReferenceById(userId));
        }
        review.setRating(request.rating());
        review.setTitle(blankToNull(request.title()));
        review.setComment(blankToNull(request.comment()));
        Review saved = reviewRepository.saveAndFlush(review);
        refreshRating(product);
        return ReviewResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteOwnReview(Long userId, Long productId) {
        Review review = reviewRepository.findByProductProductIdAndUserId(productId, userId)
                .orElseThrow(() -> ApiException.notFound("You have not reviewed this product."));
        Product product = review.getProduct();
        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshRating(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> customerReviews(Long userId, int page, int size) {
        return PageResponse.of(reviewRepository.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))), ReviewResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> adminReviews(Integer rating, int page, int size) {
        return PageResponse.of(reviewRepository.adminSearch(rating,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))), ReviewResponse::from);
    }

    @Override
    @Transactional
    public void adminDelete(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review " + reviewId + " was not found."));
        Product product = review.getProduct();
        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshRating(product);
    }

    /** Recalculates the average and count stored on the product, so listings can show stars cheaply. */
    private void refreshRating(Product product) {
        Object[] row = reviewRepository.ratingSummary(product.getProductId()).get(0);
        product.setRatingAverage(BigDecimal.valueOf(((Number) row[0]).doubleValue()).setScale(2, RoundingMode.HALF_UP));
        product.setRatingCount(((Number) row[1]).intValue());
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product " + productId + " was not found."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
