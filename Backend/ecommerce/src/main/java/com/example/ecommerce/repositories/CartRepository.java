package com.example.ecommerce.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category", "items.product.seller"})
    Optional<Cart> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "items", "items.product", "items.product.category", "items.product.seller"})
    Optional<Cart> findWithItemsByCartId(Long cartId);

    /**
     * Cart Management dashboard. state: null = every cart, ACTIVE = items and activity since cutoff,
     * ABANDONED = items but no activity since cutoff, EMPTY = no items. Search matches customer name or email.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query(value = """
            select c from Cart c
            where c.user.role = com.example.ecommerce.enums.Role.CUSTOMER
              and (:search is null
                   or lower(c.user.fullName) like lower(concat('%', :search, '%'))
                   or lower(c.user.email) like lower(concat('%', :search, '%')))
              and (:state is null
                   or (:state = 'EMPTY' and size(c.items) = 0)
                   or (:state = 'ACTIVE' and size(c.items) > 0 and c.updatedAt >= :cutoff)
                   or (:state = 'ABANDONED' and size(c.items) > 0 and c.updatedAt < :cutoff))
            order by case when size(c.items) = 0 then 1 else 0 end, c.updatedAt desc
            """,
            countQuery = """
            select count(c) from Cart c
            where c.user.role = com.example.ecommerce.enums.Role.CUSTOMER
              and (:search is null
                   or lower(c.user.fullName) like lower(concat('%', :search, '%'))
                   or lower(c.user.email) like lower(concat('%', :search, '%')))
              and (:state is null
                   or (:state = 'EMPTY' and size(c.items) = 0)
                   or (:state = 'ACTIVE' and size(c.items) > 0 and c.updatedAt >= :cutoff)
                   or (:state = 'ABANDONED' and size(c.items) > 0 and c.updatedAt < :cutoff))
            """)
    Page<Cart> adminSearch(@Param("state") String state, @Param("cutoff") LocalDateTime cutoff,
                           @Param("search") String search, Pageable pageable);

    /** Customer carts that still hold items, with their products (for the summary and abandonment analysis). */
    @EntityGraph(attributePaths = {"user", "items", "items.product", "items.product.category", "items.product.seller"})
    @Query("""
            select c from Cart c
            where c.user.role = com.example.ecommerce.enums.Role.CUSTOMER and size(c.items) > 0
            """)
    List<Cart> findNonEmptyCustomerCarts();

    @Query("select count(c) from Cart c where c.user.role = com.example.ecommerce.enums.Role.CUSTOMER")
    long countCustomerCarts();

    /** Sets the reminder time without touching updated_at (a reminder is not customer activity). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Cart c set c.reminderSentAt = :at where c.cartId = :cartId")
    int markReminderSent(@Param("cartId") Long cartId, @Param("at") LocalDateTime at);
}
