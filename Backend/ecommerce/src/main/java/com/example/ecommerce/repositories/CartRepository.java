package com.example.ecommerce.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ecommerce.entites.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category", "items.product.seller"})
    Optional<Cart> findByUserId(Long userId);
}
