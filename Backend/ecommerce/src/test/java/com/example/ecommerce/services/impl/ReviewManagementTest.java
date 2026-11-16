package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.ecommerce.dtos.review.ReviewModerationDtos.ModerationSummary;
import com.example.ecommerce.dtos.review.ReviewRequest;
import com.example.ecommerce.dtos.review.ReviewResponse;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.Review;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.ReviewStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.ReviewRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.NotificationService;

/** Review and Rating Management module: add (buyers, 1-5 stars), moderation, product page view, update/delete. */
@ExtendWith(MockitoExtension.class)
class ReviewManagementTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ReviewServiceImpl reviews;

    private Product product;
    private User priya;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setProductId(62L);
        product.setProductName("Tales from the Western Ghats");
        priya = TestCatalog.customer(8L, "Priya Sharma");
        lenient().when(productRepository.findById(62L)).thenReturn(Optional.of(product));
        lenient().when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------ add a review

    @Test
    void aNewReviewWaitsForApprovalAndAdminsAreTold() {
        when(reviewRepository.findByProductProductIdAndUserId(62L, 8L)).thenReturn(Optional.empty());
        when(orderItemRepository.hasPurchased(eq(8L), eq(62L), anyCollection())).thenReturn(true);
        when(userRepository.getReferenceById(8L)).thenReturn(priya);
        approvedSummary(0, 0);

        ReviewResponse r = reviews.saveReview(8L, 62L, new ReviewRequest(5, "Lovely stories", "Beautifully written."));

        assertThat(r.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(r.approved()).isFalse();
        // Pending reviews do not count towards the product's rating.
        assertThat(product.getRatingCount()).isZero();
        verify(notificationService).notifyAdmins(eq("New review to moderate"), anyString(), eq("/admin/reviews"));
    }

    @Test
    void ratingMustBeBetweenOneAndFiveStars() {
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new ReviewRequest(0, null, null))).isNotEmpty();
        assertThat(validator.validate(new ReviewRequest(6, null, null))).isNotEmpty();
        assertThat(validator.validate(new ReviewRequest(null, null, null))).isNotEmpty();
        assertThat(validator.validate(new ReviewRequest(3, null, "x".repeat(2001)))).isNotEmpty();
        assertThat(validator.validate(new ReviewRequest(1, null, null))).isEmpty();
        assertThat(validator.validate(new ReviewRequest(5, "Great", "Loved it"))).isEmpty();
    }

    // --------------------------------------------------------------- moderation

    @Test
    void approvingPublishesTheReviewAndRecalculatesTheRating() {
        Review review = review(ReviewStatus.PENDING, 4);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        approvedSummary(4.0, 1);

        ReviewResponse r = reviews.approve(1L);

        assertThat(r.status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(review.getApproved()).isTrue(); // Boolean status column
        assertThat(review.getModeratedAt()).isNotNull();
        assertThat(product.getRatingAverage()).isEqualByComparingTo("4.00");
        assertThat(product.getRatingCount()).isEqualTo(1);
        assertThat(r.customerEmail()).isEqualTo(priya.getEmail()); // the admin sees who wrote it
        verify(notificationService).notify(eq(priya), eq("Your review is live"), anyString(), eq("/products/62#reviews"));
    }

    @Test
    void rejectingHidesTheReviewWithAReasonForTheCustomer() {
        Review review = review(ReviewStatus.APPROVED, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        approvedSummary(0, 0);

        ReviewResponse r = reviews.reject(1L, "  Offensive or abusive language ");

        assertThat(r.status()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(review.getApproved()).isFalse();
        assertThat(review.getRejectionReason()).isEqualTo("Offensive or abusive language");
        assertThat(product.getRatingCount()).isZero();
        verify(notificationService).notify(eq(priya), eq("Your review was not published"),
                org.mockito.ArgumentMatchers.contains("Offensive or abusive language"), eq("/account/reviews"));
    }

    @Test
    void aRejectionNeedsAReason() {
        assertThatThrownBy(() -> reviews.reject(1L, " "))
                .isInstanceOf(ApiException.class).hasMessageContaining("reason");
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void approvingTwiceDoesNothingTheSecondTime() {
        Review review = review(ReviewStatus.APPROVED, 5);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        reviews.approve(1L);

        verify(reviewRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).notify(any(), anyString(), anyString(), anyString());
    }

    @Test
    void moderationSummaryCountsEachStatus() {
        when(reviewRepository.statusCounts()).thenReturn(List.of(new Object[]{ReviewStatus.PENDING, 2L},
                new Object[]{ReviewStatus.APPROVED, 5L}, new Object[]{ReviewStatus.REJECTED, 1L}));
        when(reviewRepository.approvedAverage()).thenReturn(4.2);

        ModerationSummary s = reviews.moderationSummary();

        assertThat(s.pending()).isEqualTo(2);
        assertThat(s.approved()).isEqualTo(5);
        assertThat(s.rejected()).isEqualTo(1);
        assertThat(s.total()).isEqualTo(8);
        assertThat(s.averageRating()).isEqualByComparingTo(new BigDecimal("4.20"));
    }

    @Test
    void moderationQueueFiltersByStatusRatingAndSearch() {
        when(reviewRepository.adminSearch(eq(ReviewStatus.PENDING), eq(5), eq("ghats"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review(ReviewStatus.PENDING, 5))));

        var page = reviews.adminReviews(ReviewStatus.PENDING, 5, "  ghats ", 0, 10);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).customerName()).isEqualTo("Priya Sharma");
        assertThatThrownBy(() -> reviews.adminReviews(null, 9, null, 0, 10)).isInstanceOf(ApiException.class);
    }

    // ------------------------------------------------------- view product reviews

    @Test
    void theProductPageShowsApprovedReviewsOnly() {
        when(reviewRepository.findByProductProductIdAndModerationStatusOrderByCreatedAtDesc(eq(62L),
                eq(ReviewStatus.APPROVED), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(review(ReviewStatus.APPROVED, 5))));

        var page = reviews.productReviews(62L, 0, 5);

        assertThat(page.content()).extracting(ReviewResponse::status).containsOnly(ReviewStatus.APPROVED);
        assertThat(page.content().get(0).reviewerName()).isEqualTo("Priya");
        assertThat(page.content().get(0).customerEmail()).isNull();
    }

    // --------------------------------------------------- update / delete reviews

    @Test
    void editingAnApprovedReviewSendsItBackForApproval() {
        Review review = review(ReviewStatus.APPROVED, 5);
        when(reviewRepository.findByProductProductIdAndUserId(62L, 8L)).thenReturn(Optional.of(review));
        approvedSummary(0, 0);

        ReviewResponse r = reviews.saveReview(8L, 62L, new ReviewRequest(3, "Good", "Changed my mind a little."));

        assertThat(r.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(r.rating()).isEqualTo(3);
        assertThat(review.getApproved()).isFalse();
        verify(notificationService).notifyAdmins(eq("Edited review to moderate"), anyString(), eq("/admin/reviews"));
        // The customer can edit their own review even without buying again.
        verify(orderItemRepository, never()).hasPurchased(any(), any(), anyCollection());
    }

    @Test
    void savingAnUnchangedReviewKeepsItsApproval() {
        Review review = review(ReviewStatus.APPROVED, 5);
        when(reviewRepository.findByProductProductIdAndUserId(62L, 8L)).thenReturn(Optional.of(review));
        approvedSummary(5.0, 1);

        ReviewResponse r = reviews.saveReview(8L, 62L, new ReviewRequest(5, "Lovely stories", "Beautifully written."));

        assertThat(r.status()).isEqualTo(ReviewStatus.APPROVED);
        verify(notificationService, never()).notifyAdmins(anyString(), anyString(), anyString());
    }

    @Test
    void customersDeleteTheirOwnReviewAndTheRatingIsRecalculated() {
        Review review = review(ReviewStatus.APPROVED, 2);
        when(reviewRepository.findByProductProductIdAndUserId(62L, 8L)).thenReturn(Optional.of(review));
        approvedSummary(0, 0);

        reviews.deleteOwnReview(8L, 62L);

        verify(reviewRepository).delete(review);
        assertThat(product.getRatingCount()).isZero();
        assertThatThrownBy(() -> reviews.deleteOwnReview(9L, 62L)).isInstanceOf(ApiException.class)
                .hasMessageContaining("not reviewed");
    }

    @Test
    void adminsDeleteInappropriateReviewsAndTheCustomerIsTold() {
        Review review = review(ReviewStatus.PENDING, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        approvedSummary(0, 0);

        reviews.adminDelete(1L);

        verify(reviewRepository).delete(review);
        verify(notificationService).notify(eq(priya), eq("Your review was removed"), anyString(), eq("/account/reviews"));
        when(reviewRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reviews.adminDelete(2L)).isInstanceOf(ApiException.class);
    }

    // ------------------------------------------------------------------ helpers

    private Review review(ReviewStatus status, int rating) {
        Review r = new Review();
        r.setReviewId(1L);
        r.setProduct(product);
        r.setUser(priya);
        r.setRating(rating);
        r.setTitle("Lovely stories");
        r.setComment("Beautifully written.");
        r.moderate(status, status == ReviewStatus.REJECTED ? "Spam or advertising" : null);
        return r;
    }

    private void approvedSummary(double average, long count) {
        lenient().when(reviewRepository.ratingSummary(62L)).thenReturn(List.<Object[]>of(new Object[]{average, count}));
    }
}
