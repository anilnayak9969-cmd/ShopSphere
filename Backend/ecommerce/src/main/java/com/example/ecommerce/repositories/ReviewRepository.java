package com.example.ecommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.Review;
import com.example.ecommerce.enums.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Public product page: approved reviews only, newest first. */
    @EntityGraph(attributePaths = {"user"})
    Page<Review> findByProductProductIdAndModerationStatusOrderByCreatedAtDesc(Long productId, ReviewStatus status,
                                                                                Pageable pageable);

    Optional<Review> findByProductProductIdAndUserId(Long productId, Long userId);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Row is [average, count] of the product's approved reviews. */
    @Query("""
            select coalesce(avg(r.rating), 0), count(r) from Review r
            where r.product.productId = :productId
              and r.moderationStatus = com.example.ecommerce.enums.ReviewStatus.APPROVED
            """)
    List<Object[]> ratingSummary(@Param("productId") Long productId);

    /** Rows are [rating, count] of approved reviews — used for the 5★..1★ bars. */
    @Query("""
            select r.rating, count(r) from Review r
            where r.product.productId = :productId
              and r.moderationStatus = com.example.ecommerce.enums.ReviewStatus.APPROVED
            group by r.rating
            """)
    List<Object[]> ratingDistribution(@Param("productId") Long productId);

    /** Admin moderation queue: filter by status and stars, search product, customer name/email or text. */
    @Query(value = """
            select r from Review r join fetch r.user u join fetch r.product p
            where (:status is null or r.moderationStatus = :status)
              and (:rating is null or r.rating = :rating)
              and (:search is null
                   or lower(p.productName) like lower(concat('%', :search, '%'))
                   or lower(u.fullName) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%'))
                   or lower(coalesce(r.title, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(r.comment, '')) like lower(concat('%', :search, '%')))
            order by case when r.moderationStatus = com.example.ecommerce.enums.ReviewStatus.PENDING then 0 else 1 end,
                     r.updatedAt desc
            """,
            countQuery = """
            select count(r) from Review r join r.user u join r.product p
            where (:status is null or r.moderationStatus = :status)
              and (:rating is null or r.rating = :rating)
              and (:search is null
                   or lower(p.productName) like lower(concat('%', :search, '%'))
                   or lower(u.fullName) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%'))
                   or lower(coalesce(r.title, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(r.comment, '')) like lower(concat('%', :search, '%')))
            """)
    Page<Review> adminSearch(@Param("status") ReviewStatus status, @Param("rating") Integer rating,
                             @Param("search") String search, Pageable pageable);

    /** Rows are [status, count] for the moderation cards. */
    @Query("select r.moderationStatus, count(r) from Review r group by r.moderationStatus")
    List<Object[]> statusCounts();

    /** Average stars across all approved reviews. */
    @Query("select coalesce(avg(r.rating), 0) from Review r where r.moderationStatus = com.example.ecommerce.enums.ReviewStatus.APPROVED")
    Double approvedAverage();
}
