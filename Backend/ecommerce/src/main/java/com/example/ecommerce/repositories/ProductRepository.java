package com.example.ecommerce.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.Category;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.enums.ApprovalStatus;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Loads category and seller in the same query to avoid one extra query per product (N+1). */
    @Override
    @EntityGraph(attributePaths = {"category", "seller"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "seller"})
    Optional<Product> findWithDetailsByProductId(Long productId);

    @EntityGraph(attributePaths = {"category", "seller"})
    Optional<Product> findByProductIdAndSellerId(Long productId, Long sellerId);

    boolean existsBySkuIgnoreCase(String sku);

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndProductIdNot(String sku, Long productId);

    /**
     * Product counts per category in ONE grouped query.
     * Each row is [categoryId, totalProducts, activeProducts, liveProducts, pendingProducts].
     * "Live" uses the same rule as the shop (ProductServiceImpl.isPubliclyVisible): switched on, approved by an
     * admin, in an active category, and sold by an active, approved seller. "Pending" products are waiting for admin approval.
     */
    @Query("""
            select p.category.categoryId, count(p), sum(case when p.active = true then 1 else 0 end),
                   sum(case when p.active = true
                             and p.approvalStatus = com.example.ecommerce.enums.ApprovalStatus.APPROVED
                             and p.category.status = true
                             and s.active = true
                             and s.sellerStatus = com.example.ecommerce.enums.SellerStatus.APPROVED
                            then 1 else 0 end),
                   sum(case when p.approvalStatus = com.example.ecommerce.enums.ApprovalStatus.PENDING then 1 else 0 end)
            from Product p join p.seller s
            where p.category.categoryId in :categoryIds
            group by p.category.categoryId
            """)
    List<Object[]> countByCategoryIds(@Param("categoryIds") Collection<Integer> categoryIds);

    long countByCategoryCategoryIdAndActiveTrue(Integer categoryId);

    @Modifying
    @Query("update Product p set p.category = :target, p.updatedAt = :now where p.category = :source")
    int reassignCategory(@Param("source") Category source, @Param("target") Category target,
                         @Param("now") LocalDateTime now);

    /**
     * SELECT ... FOR UPDATE: locks these product rows until the transaction ends, so two
     * customers paying at the same moment cannot both buy the last unit.
     * Ordered by id so concurrent transactions always lock in the same order (no deadlocks).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId in :ids order by p.productId")
    List<Product> lockByIds(@Param("ids") Collection<Long> ids);

    @Query("""
            select p from Product p
            where p.active = true and p.category.status = true
              and p.approvalStatus = com.example.ecommerce.enums.ApprovalStatus.APPROVED
              and p.seller.active = true and p.seller.sellerStatus = com.example.ecommerce.enums.SellerStatus.APPROVED
              and lower(p.productName) like lower(concat('%', :q, '%'))
            order by p.soldCount desc, p.productName
            """)
    List<Product> suggest(@Param("q") String q, Pageable pageable);

    /** Brands customers can filter by: only brands of products currently visible in the store. */
    @Query("""
            select distinct p.brand from Product p
            where p.brand is not null and p.active = true and p.category.status = true
              and p.approvalStatus = com.example.ecommerce.enums.ApprovalStatus.APPROVED
              and p.seller.active = true and p.seller.sellerStatus = com.example.ecommerce.enums.SellerStatus.APPROVED
            order by p.brand
            """)
    List<String> visibleBrands();

    @Query("""
            select distinct p.brand from Product p
            where p.brand is not null and p.active = true and p.category.status = true
              and p.approvalStatus = com.example.ecommerce.enums.ApprovalStatus.APPROVED
              and p.seller.active = true and p.seller.sellerStatus = com.example.ecommerce.enums.SellerStatus.APPROVED
              and p.category.categoryId in :categoryIds
            order by p.brand
            """)
    List<String> visibleBrandsIn(@Param("categoryIds") Collection<Integer> categoryIds);

    long countBySellerId(Long sellerId);

    long countBySellerIdAndActiveTrue(Long sellerId);

    long countBySellerIdAndStockQuantityLessThanEqual(Long sellerId, int threshold);

    long countBySellerIdAndApprovalStatus(Long sellerId, ApprovalStatus status);

    long countByActiveTrue();

    long countByApprovalStatus(ApprovalStatus status);

    long countByActiveTrueAndStockQuantityLessThanEqual(int threshold);

    @EntityGraph(attributePaths = {"category", "seller"})
    List<Product> findTop10ByActiveTrueAndStockQuantityLessThanEqualOrderByStockQuantityAsc(int threshold);
}
