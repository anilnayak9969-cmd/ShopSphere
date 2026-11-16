package com.example.ecommerce.repositories;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.ecommerce.entites.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** Refund history for the admin payment dashboard, newest first. */
    @EntityGraph(attributePaths = {"payment", "payment.order", "payment.order.customer"})
    @Query(value = "select r from Refund r order by r.createdAt desc, r.refundId desc",
            countQuery = "select count(r) from Refund r")
    Page<Refund> history(Pageable pageable);

    List<Refund> findByPaymentPaymentIdOrderByCreatedAtAsc(Long paymentId);

    long countByStatus(Refund.Status status);

    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.status = com.example.ecommerce.entites.Refund.Status.PROCESSED")
    BigDecimal sumProcessed();
}
