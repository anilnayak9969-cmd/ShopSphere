package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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

import com.example.ecommerce.dtos.review.ReviewEligibility;
import com.example.ecommerce.dtos.review.ReviewRequest;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.Review;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.ReviewRepository;
import com.example.ecommerce.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setProductId(10L);
        product.setProductName("Aura Headphones");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(reviewRepository.findByProductProductIdAndUserId(10L, 7L)).thenReturn(Optional.empty());
    }

    @Test
    void customerWhoNeverBoughtCannotReview() {
        when(orderItemRepository.hasPurchased(eq(7L), eq(10L), anyCollection())).thenReturn(false);

        ReviewEligibility eligibility = reviewService.eligibility(7L, 10L);
        assertThat(eligibility.canReview()).isFalse();

        assertThatThrownBy(() -> reviewService.saveReview(7L, 10L, new ReviewRequest(5, "Great", "Loved it")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("NOT_PURCHASED"));
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void buyerCanReviewAndProductRatingIsRecalculated() {
        User reviewer = new User();
        reviewer.setFullName("Riya Sharma");
        when(orderItemRepository.hasPurchased(eq(7L), eq(10L), anyCollection())).thenReturn(true);
        when(userRepository.getReferenceById(7L)).thenReturn(reviewer);
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.ratingSummary(10L)).thenReturn(List.<Object[]>of(new Object[]{4.5, 2L}));

        var response = reviewService.saveReview(7L, 10L, new ReviewRequest(4, "  Comfortable  ", ""));

        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.title()).isEqualTo("Comfortable");
        assertThat(response.comment()).isNull();
        assertThat(response.reviewerName()).isEqualTo("Riya");
        assertThat(product.getRatingAverage()).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(product.getRatingCount()).isEqualTo(2);
    }
}
