package com.example.ecommerce.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.OrderStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * A seller only ever sees the lines of an order that belong to their own products,
     * and only once the order is placed (unpaid online checkouts are hidden).
     */
    @Query(value = """
            select oi from OrderItem oi join fetch oi.order o
            where oi.seller.id = :sellerId and o.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT
              and (:status is null or oi.itemStatus = :status)
            order by o.createdAt desc
            """,
            countQuery = """
            select count(oi) from OrderItem oi
            where oi.seller.id = :sellerId and oi.order.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT
              and (:status is null or oi.itemStatus = :status)
            """)
    Page<OrderItem> findSellerItems(@Param("sellerId") Long sellerId, @Param("status") OrderStatus status,
                                    Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.customer", "product"})
    Optional<OrderItem> findByOrderItemIdAndSellerId(Long orderItemId, Long sellerId);

    // ------------------------------------------------------------ delivery partners

    /** Packed items nobody has picked up yet; deliveries in the partner's own city come first. */
    @Query(value = """
            select oi from OrderItem oi join fetch oi.order o join fetch oi.seller s
            where oi.itemStatus = com.example.ecommerce.enums.OrderStatus.PACKED and oi.deliveryPartner is null
            order by case when lower(o.city) = lower(:city) then 0 else 1 end, o.createdAt
            """,
            countQuery = """
            select count(oi) from OrderItem oi
            where oi.itemStatus = com.example.ecommerce.enums.OrderStatus.PACKED and oi.deliveryPartner is null
            """)
    Page<OrderItem> findAvailablePickups(@Param("city") String city, Pageable pageable);

    @Query(value = """
            select oi from OrderItem oi join fetch oi.order o join fetch oi.seller s
            where oi.deliveryPartner.id = :partnerId and oi.itemStatus in :statuses
            order by coalesce(oi.deliveredAt, oi.assignedAt) desc
            """,
            countQuery = """
            select count(oi) from OrderItem oi
            where oi.deliveryPartner.id = :partnerId and oi.itemStatus in :statuses
            """)
    Page<OrderItem> findPartnerItems(@Param("partnerId") Long partnerId,
                                     @Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);

    @Query("select count(oi) from OrderItem oi where oi.deliveryPartner.id = :partnerId and oi.itemStatus in :statuses")
    long countPartnerItems(@Param("partnerId") Long partnerId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select count(oi) from OrderItem oi where oi.deliveryPartner.id = :partnerId and oi.deliveredAt >= :from")
    long countPartnerDeliveredSince(@Param("partnerId") Long partnerId, @Param("from") LocalDateTime from);

    @Query("""
            select count(oi) from OrderItem oi
            where oi.itemStatus = com.example.ecommerce.enums.OrderStatus.PACKED and oi.deliveryPartner is null
            """)
    long countAvailablePickups();

    /**
     * Atomically assigns a packed item to a partner. Returns 0 when someone else accepted it first,
     * so two partners can never take the same parcel.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OrderItem oi set oi.deliveryPartner = :partner, oi.assignedAt = :now
            where oi.orderItemId = :itemId and oi.deliveryPartner is null
              and oi.itemStatus = com.example.ecommerce.enums.OrderStatus.PACKED
            """)
    int claimPickup(@Param("itemId") Long itemId, @Param("partner") User partner, @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = {"order", "order.customer", "product", "seller"})
    Optional<OrderItem> findWithOrderByOrderItemId(Long orderItemId);

    /** Row is [unitsSold, revenue] over items in the given statuses. */
    @Query("""
            select coalesce(sum(oi.quantity), 0), coalesce(sum(oi.lineTotal), 0)
            from OrderItem oi
            where oi.seller.id = :sellerId and oi.itemStatus in :statuses
            """)
    List<Object[]> sellerTotals(@Param("sellerId") Long sellerId,
                                @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select count(oi) from OrderItem oi where oi.seller.id = :sellerId and oi.itemStatus in :statuses")
    long countSellerItems(@Param("sellerId") Long sellerId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select count(distinct oi.order.orderId) from OrderItem oi where oi.seller.id = :sellerId and oi.itemStatus in :statuses")
    long countSellerOrders(@Param("sellerId") Long sellerId, @Param("statuses") Collection<OrderStatus> statuses);

    /** "Verified purchase": has this customer received or been sold this product? */
    @Query("""
            select count(oi) > 0 from OrderItem oi
            where oi.order.customer.id = :customerId and oi.product.productId = :productId
              and oi.itemStatus in :statuses
            """)
    boolean hasPurchased(@Param("customerId") Long customerId, @Param("productId") Long productId,
                         @Param("statuses") Collection<OrderStatus> statuses);

    // ------------------------------------------------------------ reports

    /** Rows are [date, revenue, units] per day for one seller. */
    @Query("""
            select cast(oi.order.createdAt as LocalDate), coalesce(sum(oi.lineTotal), 0), coalesce(sum(oi.quantity), 0)
            from OrderItem oi
            where oi.seller.id = :sellerId and oi.itemStatus in :statuses and oi.order.createdAt >= :from
            group by cast(oi.order.createdAt as LocalDate)
            order by cast(oi.order.createdAt as LocalDate)
            """)
    List<Object[]> sellerDailySales(@Param("sellerId") Long sellerId,
                                    @Param("statuses") Collection<OrderStatus> statuses,
                                    @Param("from") LocalDateTime from);

    /** Rows are [productName, units, revenue] — the seller's best products. */
    @Query("""
            select oi.productName, sum(oi.quantity), sum(oi.lineTotal)
            from OrderItem oi
            where oi.seller.id = :sellerId and oi.itemStatus in :statuses
            group by oi.product.productId, oi.productName
            order by sum(oi.lineTotal) desc
            """)
    List<Object[]> sellerTopProducts(@Param("sellerId") Long sellerId,
                                     @Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);

    /** Rows are [status, count] of the seller's order lines. */
    @Query("""
            select oi.itemStatus, count(oi) from OrderItem oi
            where oi.seller.id = :sellerId and oi.order.status <> com.example.ecommerce.enums.OrderStatus.PENDING_PAYMENT
            group by oi.itemStatus
            """)
    List<Object[]> sellerStatusCounts(@Param("sellerId") Long sellerId);

    /** Rows are [year, month, deliveredRevenue, units] — the basis of the commission statement. */
    @Query("""
            select year(oi.deliveredAt), month(oi.deliveredAt), sum(oi.lineTotal), sum(oi.quantity)
            from OrderItem oi
            where oi.seller.id = :sellerId and oi.itemStatus = com.example.ecommerce.enums.OrderStatus.DELIVERED
              and oi.deliveredAt is not null
            group by year(oi.deliveredAt), month(oi.deliveredAt)
            order by year(oi.deliveredAt) desc, month(oi.deliveredAt) desc
            """)
    List<Object[]> sellerMonthlyDelivered(@Param("sellerId") Long sellerId);

    /** Rows are [categoryName, units, revenue] across the platform. */
    @Query("""
            select oi.product.category.categoryName, sum(oi.quantity), sum(oi.lineTotal)
            from OrderItem oi
            where oi.itemStatus in :statuses
            group by oi.product.category.categoryName
            order by sum(oi.lineTotal) desc
            """)
    List<Object[]> categorySales(@Param("statuses") Collection<OrderStatus> statuses);

    /** Rows are [storeName, units, revenue] across the platform. */
    @Query("""
            select oi.storeName, sum(oi.quantity), sum(oi.lineTotal)
            from OrderItem oi
            where oi.itemStatus in :statuses
            group by oi.seller.id, oi.storeName
            order by sum(oi.lineTotal) desc
            """)
    List<Object[]> sellerSales(@Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);
}
