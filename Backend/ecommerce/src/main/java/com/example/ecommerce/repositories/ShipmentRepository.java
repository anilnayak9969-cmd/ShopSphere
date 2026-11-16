package com.example.ecommerce.repositories;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.Shipment;
import com.example.ecommerce.enums.ShippingStatus;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    @EntityGraph(attributePaths = {"order", "order.customer"})
    Optional<Shipment> findByOrderOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    Optional<Shipment> findFirstByTrackingNumberIgnoreCase(String trackingNumber);

    boolean existsByTrackingNumberIgnoreCaseAndOrderOrderIdNot(String trackingNumber, Long orderId);

    /**
     * Shipping dashboard: every placed order's shipment (unpaid checkouts are left out). anyStatus = true
     * ignores the status filter. Search matches order number, tracking number, courier, customer and city.
     */
    @EntityGraph(attributePaths = {"order", "order.customer"})
    @Query(value = """
            select s from Shipment s
            where s.order.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT
              and (:anyStatus = true or s.shippingStatus in :statuses)
              and (:search is null
                   or lower(s.order.orderNumber) like lower(concat('%', :search, '%'))
                   or lower(s.trackingNumber) like lower(concat('%', :search, '%'))
                   or lower(s.courierService) like lower(concat('%', :search, '%'))
                   or lower(s.order.customer.fullName) like lower(concat('%', :search, '%'))
                   or lower(s.order.city) like lower(concat('%', :search, '%')))
            order by s.order.createdAt desc
            """,
            countQuery = """
            select count(s) from Shipment s
            where s.order.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT
              and (:anyStatus = true or s.shippingStatus in :statuses)
              and (:search is null
                   or lower(s.order.orderNumber) like lower(concat('%', :search, '%'))
                   or lower(s.trackingNumber) like lower(concat('%', :search, '%'))
                   or lower(s.courierService) like lower(concat('%', :search, '%'))
                   or lower(s.order.customer.fullName) like lower(concat('%', :search, '%'))
                   or lower(s.order.city) like lower(concat('%', :search, '%')))
            """)
    Page<Shipment> search(@Param("anyStatus") boolean anyStatus, @Param("statuses") Collection<ShippingStatus> statuses,
                          @Param("search") String search, Pageable pageable);

    /** Rows are [status, count] for placed orders. */
    @Query("""
            select s.shippingStatus, count(s) from Shipment s
            where s.order.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT
            group by s.shippingStatus
            """)
    List<Object[]> statusCounts();

    /** Shipping charged on orders that were not cancelled. */
    @Query("""
            select coalesce(sum(s.shippingCost), 0) from Shipment s
            where s.order.status not in (com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT,
                                         com.example.ecommerce.enums.OrderStatus.CANCELLED)
            """)
    BigDecimal shippingRevenue();

    @Query("""
            select count(s) from Shipment s
            where s.shippingMethod = com.example.ecommerce.enums.ShippingMethod.EXPRESS
              and s.order.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT
            """)
    long countExpress();
}
