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

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {"user"})
    Page<Review> findByProductProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    Optional<Review> findByProductProductIdAndUserId(Long productId, Long userId);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Row is [average, count]. */
    @Query("select coalesce(avg(r.rating), 0), count(r) from Review r where r.product.productId = :productId")
    List<Object[]> ratingSummary(@Param("productId") Long productId);

    /** Rows are [rating, count] — used for the 5★..1★ bars. */
    @Query("select r.rating, count(r) from Review r where r.product.productId = :productId group by r.rating")
    List<Object[]> ratingDistribution(@Param("productId") Long productId);

    @Query(value = """
            select r from Review r join fetch r.user join fetch r.product
            where (:rating is null or r.rating = :rating)
            order by r.createdAt desc
            """,
            countQuery = "select count(r) from Review r where (:rating is null or r.rating = :rating)")
    Page<Review> adminSearch(@Param("rating") Integer rating, Pageable pageable);
}
