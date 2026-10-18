package com.example.ecommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ecommerce.entites.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @EntityGraph(attributePaths = {"product", "product.category", "product.seller"})
    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WishlistItem> findByUserIdAndProductProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductProductId(Long userId, Long productId);

    long countByUserId(Long userId);
}
