package com.example.ecommerce.services;

import java.math.BigDecimal;
import java.util.List;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.product.ProductRequest;
import com.example.ecommerce.dtos.product.ProductResponse;
import com.example.ecommerce.dtos.product.ProductSuggestion;
import com.example.ecommerce.enums.ApprovalStatus;

public interface ProductService {

    /** All storefront filters; null values are ignored. */
    record CatalogQuery(String search, Integer categoryId, List<String> brands, BigDecimal minPrice,
                        BigDecimal maxPrice, Boolean inStock, BigDecimal minRating, Integer minDiscount,
                        String sort, int page, int size) {
    }

    // Storefront
    PageResponse<ProductResponse> searchPublic(CatalogQuery query);

    ProductResponse getPublicProduct(Long productId);

    List<ProductSuggestion> suggest(String query);

    /** Brands that have visible products (optionally within a category and its subcategories). */
    List<String> brands(Integer categoryId);

    // Seller (always scoped to the logged-in seller)
    PageResponse<ProductResponse> sellerProducts(Long sellerId, String search, ApprovalStatus approval,
                                                 Integer maxStock, int page, int size);

    ProductResponse sellerProduct(Long sellerId, Long productId);

    ProductResponse createProduct(Long sellerId, ProductRequest request);

    ProductResponse updateProduct(Long sellerId, Long productId, ProductRequest request);

    ProductResponse setSellerProductActive(Long sellerId, Long productId, boolean active);

    ProductResponse updateStock(Long sellerId, Long productId, int stockQuantity);

    // Admin
    PageResponse<ProductResponse> adminProducts(String search, Integer categoryId, ApprovalStatus approval,
                                                int page, int size);

    ProductResponse adminSetActive(Long productId, boolean active);

    ProductResponse approve(Long productId);

    /** Admin catalogue clean-up: moves a product to another active category without sending it back for review. */
    ProductResponse moveToCategory(Long productId, Integer categoryId);

    ProductResponse reject(Long productId, String reason);
}
