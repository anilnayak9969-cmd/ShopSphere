package com.example.ecommerce.repositories;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.ecommerce.entites.Product;
import com.example.ecommerce.enums.ApprovalStatus;
import com.example.ecommerce.enums.SellerStatus;

import jakarta.persistence.criteria.Predicate;

/**
 * Builds the WHERE clause for product searches dynamically: only the filters the user actually
 * supplied are added, so the database does the filtering instead of the browser.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** All supported filters. Null fields are ignored. */
    public record Filter(
            boolean publicOnly,
            Long sellerId,
            String search,
            Collection<Integer> categoryIds,
            Collection<String> brands,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            BigDecimal minRating,
            Integer minDiscount,
            ApprovalStatus approvalStatus,
            Integer maxStock) {

        public static Filter publicCatalog(String search, Collection<Integer> categoryIds, Collection<String> brands,
                                           BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock,
                                           BigDecimal minRating, Integer minDiscount) {
            return new Filter(true, null, search, categoryIds, brands, minPrice, maxPrice, inStock, minRating,
                    minDiscount, null, null);
        }

        public static Filter forSeller(Long sellerId, String search, ApprovalStatus status, Integer maxStock) {
            return new Filter(false, sellerId, search, null, null, null, null, null, null, null, status, maxStock);
        }

        public static Filter forAdmin(String search, Collection<Integer> categoryIds, ApprovalStatus status) {
            return new Filter(false, null, search, categoryIds, null, null, null, null, null, null, status, null);
        }
    }

    public static Specification<Product> filter(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (f.publicOnly()) {
                predicates.add(cb.isTrue(root.get("active")));
                predicates.add(cb.isTrue(root.get("category").get("status")));
                predicates.add(cb.equal(root.get("approvalStatus"), ApprovalStatus.APPROVED));
                predicates.add(cb.isTrue(root.get("seller").get("active")));
                predicates.add(cb.equal(root.get("seller").get("sellerStatus"), SellerStatus.APPROVED));
            }
            if (f.sellerId() != null) {
                predicates.add(cb.equal(root.get("seller").get("id"), f.sellerId()));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("productName")), like),
                        cb.like(cb.lower(root.get("brand")), like),
                        cb.like(cb.lower(root.get("sku")), like),
                        cb.like(cb.lower(root.get("category").get("categoryName")), like)));
            }
            if (f.categoryIds() != null && !f.categoryIds().isEmpty()) {
                predicates.add(root.get("category").get("categoryId").in(f.categoryIds()));
            }
            if (f.brands() != null && !f.brands().isEmpty()) {
                predicates.add(cb.lower(root.get("brand")).in(f.brands().stream().map(String::toLowerCase).toList()));
            }
            if (f.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sellingPrice"), f.minPrice()));
            }
            if (f.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sellingPrice"), f.maxPrice()));
            }
            if (Boolean.TRUE.equals(f.inStock())) {
                predicates.add(cb.greaterThan(root.get("stockQuantity"), 0));
            }
            if (f.minRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("ratingAverage"), f.minRating()));
                predicates.add(cb.greaterThan(root.get("ratingCount"), 0));
            }
            if (f.minDiscount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("discountPercent"), f.minDiscount()));
            }
            if (f.approvalStatus() != null) {
                predicates.add(cb.equal(root.get("approvalStatus"), f.approvalStatus()));
            }
            if (f.maxStock() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("stockQuantity"), f.maxStock()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
