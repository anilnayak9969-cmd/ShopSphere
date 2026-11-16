package com.example.ecommerce.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * SELECT ... FOR UPDATE on the payment row. The browser callback and the Razorpay webhook can
     * arrive at the same moment; the lock makes the second one wait and then see SUCCESS.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.razorpayOrderId = :razorpayOrderId")
    Optional<Payment> lockByRazorpayOrderId(@Param("razorpayOrderId") String razorpayOrderId);

    Optional<Payment> findTopByOrderOrderIdOrderByCreatedAtDesc(Long orderId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

    @Query("select coalesce(sum(p.refundedAmount), 0) from Payment p")
    BigDecimal sumRefunded();

    long countByStatus(PaymentStatus status);

    /** Online payments whose payment method has not been recorded yet. */
    List<Payment> findByInstrumentIsNullAndRazorpayPaymentIdIsNotNull();

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.method = :method and p.status = :status")
    BigDecimal sumAmountByMethodAndStatus(@Param("method") com.example.ecommerce.enums.PaymentMethod method,
                                          @Param("status") PaymentStatus status);

    /**
     * Payment dashboard. status / refundedOnly / method filter, search matches the order number,
     * customer name or email, and the Razorpay payment id.
     */
    @EntityGraph(attributePaths = {"order", "order.customer"})
    @Query(value = """
            select p from Payment p
            where (:status is null or p.status = :status)
              and (:refundedOnly = false or p.refundedAmount > 0)
              and (:method is null or p.method = :method)
              and (:search is null
                   or lower(p.order.orderNumber) like lower(concat('%', :search, '%'))
                   or lower(p.order.customer.fullName) like lower(concat('%', :search, '%'))
                   or lower(p.order.customer.email) like lower(concat('%', :search, '%'))
                   or lower(p.razorpayPaymentId) like lower(concat('%', :search, '%')))
            order by p.createdAt desc
            """,
            countQuery = """
            select count(p) from Payment p
            where (:status is null or p.status = :status)
              and (:refundedOnly = false or p.refundedAmount > 0)
              and (:method is null or p.method = :method)
              and (:search is null
                   or lower(p.order.orderNumber) like lower(concat('%', :search, '%'))
                   or lower(p.order.customer.fullName) like lower(concat('%', :search, '%'))
                   or lower(p.order.customer.email) like lower(concat('%', :search, '%'))
                   or lower(p.razorpayPaymentId) like lower(concat('%', :search, '%')))
            """)
    Page<Payment> dashboard(@Param("status") PaymentStatus status, @Param("refundedOnly") boolean refundedOnly,
                            @Param("method") com.example.ecommerce.enums.PaymentMethod method,
                            @Param("search") String search, Pageable pageable);

    /** Payment history for one customer, hiding abandoned checkout attempts that never got a payment. */
    @EntityGraph(attributePaths = {"order"})
    @Query(value = """
            select p from Payment p
            where p.order.customer.id = :customerId and p.status <> com.example.ecommerce.enums.PaymentStatus.CREATED
            order by p.createdAt desc
            """,
            countQuery = """
            select count(p) from Payment p
            where p.order.customer.id = :customerId and p.status <> com.example.ecommerce.enums.PaymentStatus.CREATED
            """)
    Page<Payment> customerPayments(@Param("customerId") Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    @Query(value = """
            select p from Payment p
            where (:status is null or p.status = :status)
              and (:refundedOnly = false or p.refundedAmount > 0)
            order by p.createdAt desc
            """,
            countQuery = """
            select count(p) from Payment p
            where (:status is null or p.status = :status) and (:refundedOnly = false or p.refundedAmount > 0)
            """)
    Page<Payment> adminSearch(@Param("status") PaymentStatus status, @Param("refundedOnly") boolean refundedOnly,
                              Pageable pageable);
}
