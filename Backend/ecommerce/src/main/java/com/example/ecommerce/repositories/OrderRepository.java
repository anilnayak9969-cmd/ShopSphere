package com.example.ecommerce.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.enums.OrderStatus;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    Page<CustomerOrder> findByCustomerIdAndStatusNotOrderByCreatedAtDesc(Long customerId, OrderStatus excluded,
                                                                          Pageable pageable);

    @EntityGraph(attributePaths = {"items", "customer"})
    Optional<CustomerOrder> findByOrderIdAndCustomerId(Long orderId, Long customerId);

    @EntityGraph(attributePaths = {"items", "customer"})
    Optional<CustomerOrder> findWithItemsByOrderId(Long orderId);

    @Query("""
            select o from CustomerOrder o
            where (:status is null or o.status = :status)
              and (:search is null
                   or lower(o.orderNumber) like lower(concat('%', :search, '%'))
                   or lower(o.customer.email) like lower(concat('%', :search, '%')))
            order by o.createdAt desc
            """)
    Page<CustomerOrder> search(@Param("status") OrderStatus status, @Param("search") String search, Pageable pageable);

    long countByStatus(OrderStatus status);

    long countByStatusNot(OrderStatus status);

    long countByStatusIn(Collection<OrderStatus> statuses);

    /** Rows are [date, orders, revenue] for placed (non-pending) orders since a date. */
    @Query("""
            select cast(o.createdAt as LocalDate), count(o), coalesce(sum(o.totalAmount), 0)
            from CustomerOrder o
            where o.status in :statuses and o.createdAt >= :from
            group by cast(o.createdAt as LocalDate)
            order by cast(o.createdAt as LocalDate)
            """)
    List<Object[]> dailyOrders(@Param("statuses") Collection<OrderStatus> statuses, @Param("from") LocalDateTime from);

    /** Rows are [status, count]. */
    @Query("select o.status, count(o) from CustomerOrder o where o.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT group by o.status")
    List<Object[]> statusCounts();
}
