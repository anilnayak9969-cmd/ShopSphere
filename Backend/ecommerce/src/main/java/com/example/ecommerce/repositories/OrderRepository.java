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

    /**
     * Order dashboard search. With anyStatus = true every status matches; otherwise only the given
     * statuses do (one status, or a group such as "Pending" = placed, confirmed and packed).
     */
    @Query("""
            select o from CustomerOrder o
            where (:anyStatus = true or o.status in :statuses)
              and (:search is null
                   or lower(o.orderNumber) like lower(concat('%', :search, '%'))
                   or lower(o.customer.email) like lower(concat('%', :search, '%'))
                   or lower(o.customer.fullName) like lower(concat('%', :search, '%'))
                   or lower(o.shippingName) like lower(concat('%', :search, '%'))
                   or lower(o.city) like lower(concat('%', :search, '%')))
            order by o.createdAt desc
            """)
    Page<CustomerOrder> search(@Param("anyStatus") boolean anyStatus, @Param("statuses") Collection<OrderStatus> statuses,
                               @Param("search") String search, Pageable pageable);

    /**
     * Customer Management: order history totals per customer. Rows are
     * [customerId, orders placed, amount spent on orders that were not cancelled or returned, last order time].
     * Unpaid checkout attempts are ignored.
     */
    @Query("""
            select o.customer.id, count(o),
                   coalesce(sum(case when o.status in :notSpent then 0 else o.totalAmount end), 0),
                   max(o.createdAt)
            from CustomerOrder o
            where o.customer.id in :customerIds
              and o.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT
            group by o.customer.id
            """)
    List<Object[]> customerOrderStats(@Param("customerIds") Collection<Long> customerIds,
                                      @Param("notSpent") Collection<OrderStatus> notSpent);

    @Query("""
            select count(distinct o.customer.id) from CustomerOrder o
            where o.customer.role = com.example.ecommerce.enums.Role.CUSTOMER
              and o.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT
            """)
    long countCustomersWithOrders();

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
