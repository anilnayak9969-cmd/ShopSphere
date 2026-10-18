package com.example.ecommerce.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.ReturnRequest;
import com.example.ecommerce.enums.ReturnStatus;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    boolean existsByOrderItemOrderItemId(Long orderItemId);

    List<ReturnRequest> findByOrderItemOrderItemIdIn(Collection<Long> orderItemIds);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "customer", "seller"})
    Optional<ReturnRequest> findWithDetailsByReturnId(Long returnId);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "customer", "seller"})
    Page<ReturnRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "customer", "seller"})
    @Query(value = "select r from ReturnRequest r where r.seller.id = :sellerId and (:status is null or r.status = :status) order by r.createdAt desc",
            countQuery = "select count(r) from ReturnRequest r where r.seller.id = :sellerId and (:status is null or r.status = :status)")
    Page<ReturnRequest> sellerReturns(@Param("sellerId") Long sellerId, @Param("status") ReturnStatus status,
                                      Pageable pageable);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "customer", "seller"})
    @Query(value = "select r from ReturnRequest r where (:status is null or r.status = :status) order by r.createdAt desc",
            countQuery = "select count(r) from ReturnRequest r where (:status is null or r.status = :status)")
    Page<ReturnRequest> adminSearch(@Param("status") ReturnStatus status, Pageable pageable);

    long countByStatus(ReturnStatus status);

    long countBySellerIdAndStatus(Long sellerId, ReturnStatus status);
}
