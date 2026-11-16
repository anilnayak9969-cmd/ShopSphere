package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.review.ReviewEligibility;
import com.example.ecommerce.dtos.review.ReviewModerationDtos.ModerationSummary;
import com.example.ecommerce.dtos.review.ReviewRequest;
import com.example.ecommerce.dtos.review.ReviewResponse;
import com.example.ecommerce.dtos.review.ReviewSummary;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.Review;
import com.example.ecommerce.enums.ReviewStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.ReviewRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.ReviewService;

import lombok.RequiredArgsConstructor;

/**
 * Review and Rating Management. Only buyers whose item was delivered can review (1-5 stars + text).
 * New and edited reviews wait for an admin: only APPROVED reviews are shown on the product page and
 * count towards the product's average rating.
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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
        return PageResponse.of(reviewRepository.findByProductProductIdAndModerationStatusOrderByCreatedAtDesc(productId,
                ReviewStatus.APPROVED, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 20))),
                ReviewResponse::from);
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
        boolean isNew = review == null;
        if (isNew) {
            if (!orderItemRepository.hasPurchased(userId, productId, OrderServiceImpl.RECEIVED_ITEM_STATUSES)) {
                throw ApiException.forbidden("NOT_PURCHASED", "You can review this product once it has been delivered to you.");
            }
            review = new Review();
            review.setProduct(product);
            review.setUser(userRepository.getReferenceById(userId));
        }
        String title = blankToNull(request.title());
        String comment = blankToNull(request.comment());
        boolean changed = isNew || review.getRating() != request.rating()
                || !Objects.equals(review.getTitle(), title) || !Objects.equals(review.getComment(), comment);
        review.setRating(request.rating());
        review.setTitle(title);
        review.setComment(comment);
        if (changed) {
            // New or edited text is checked again before it is shown (an approved review is hidden meanwhile).
            review.moderate(ReviewStatus.PENDING, null);
        }
        Review saved = reviewRepository.saveAndFlush(review);
        refreshRating(product);
        if (changed) {
            notificationService.notifyAdmins(isNew ? "New review to moderate" : "Edited review to moderate",
                    request.rating() + "★ review of " + product.getProductName() + " is awaiting approval.",
                    "/admin/reviews");
        }
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

    // -------------------------------------------------------------- moderation

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> adminReviews(ReviewStatus status, Integer rating, String search, int page, int size) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw ApiException.badRequest("INVALID_RATING", "Rating filter must be 1-5.");
        }
        return PageResponse.of(reviewRepository.adminSearch(status, rating, blankToNull(search),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))), ReviewResponse::forAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationSummary moderationSummary() {
        Map<ReviewStatus, Long> counts = new EnumMap<>(ReviewStatus.class);
        for (Object[] row : reviewRepository.statusCounts()) {
            if (row[0] != null) {
                counts.put((ReviewStatus) row[0], ((Number) row[1]).longValue());
            }
        }
        long pending = counts.getOrDefault(ReviewStatus.PENDING, 0L);
        long approved = counts.getOrDefault(ReviewStatus.APPROVED, 0L);
        long rejected = counts.getOrDefault(ReviewStatus.REJECTED, 0L);
        Double average = reviewRepository.approvedAverage();
        return new ModerationSummary(pending, approved, rejected, pending + approved + rejected,
                BigDecimal.valueOf(average == null ? 0 : average).setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    @Transactional
    public ReviewResponse approve(Long reviewId) {
        Review review = findReview(reviewId);
        if (review.getModerationStatus() != ReviewStatus.APPROVED) {
            review.moderate(ReviewStatus.APPROVED, null);
            reviewRepository.saveAndFlush(review);
            refreshRating(review.getProduct());
            notificationService.notify(review.getUser(), "Your review is live",
                    "Your " + review.getRating() + "★ review of " + review.getProduct().getProductName()
                            + " was approved and is now shown on the product page.",
                    "/products/" + review.getProduct().getProductId() + "#reviews");
        }
        return ReviewResponse.forAdmin(review);
    }

    @Override
    @Transactional
    public ReviewResponse reject(Long reviewId, String reason) {
        String why = blankToNull(reason);
        if (why == null || why.length() < 3) {
            throw ApiException.badRequest("REASON_REQUIRED", "Give a reason for rejecting the review.");
        }
        Review review = findReview(reviewId);
        review.moderate(ReviewStatus.REJECTED, why);
        reviewRepository.saveAndFlush(review);
        refreshRating(review.getProduct());
        notificationService.notify(review.getUser(), "Your review was not published",
                "Your review of " + review.getProduct().getProductName() + " was not approved: " + why
                        + ". You can edit it and submit it again.", "/account/reviews");
        return ReviewResponse.forAdmin(review);
    }

    @Override
    @Transactional
    public void adminDelete(Long reviewId) {
        Review review = findReview(reviewId);
        Product product = review.getProduct();
        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshRating(product);
        notificationService.notify(review.getUser(), "Your review was removed",
                "Your review of " + product.getProductName() + " was removed because it did not follow the review guidelines.",
                "/account/reviews");
    }

    /** Recalculates the average and count (approved reviews only) stored on the product, so listings show stars cheaply. */
    private void refreshRating(Product product) {
        Object[] row = reviewRepository.ratingSummary(product.getProductId()).get(0);
        product.setRatingAverage(BigDecimal.valueOf(((Number) row[0]).doubleValue()).setScale(2, RoundingMode.HALF_UP));
        product.setRatingCount(((Number) row[1]).intValue());
    }

    private Review findReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review " + reviewId + " was not found."));
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product " + productId + " was not found."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
