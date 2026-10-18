package com.example.ecommerce.dtos.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.ProductImage;
import com.example.ecommerce.enums.ApprovalStatus;

public record ProductResponse(
        Long productId,
        String productName,
        String description,
        String sku,
        String brand,
        BigDecimal price,
        int discountPercent,
        BigDecimal sellingPrice,
        int stockQuantity,
        boolean inStock,
        String imageUrl,
        List<String> galleryImages,
        List<SpecificationDto> specifications,
        BigDecimal ratingAverage,
        int ratingCount,
        int soldCount,
        boolean active,
        ApprovalStatus approvalStatus,
        String rejectionReason,
        Integer categoryId,
        String categoryName,
        boolean categoryActive,
        Long sellerId,
        String storeName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** For lists: gallery and specifications are left empty so a page of products needs no extra queries. */
    public static ProductResponse from(Product p) {
        return build(p, List.of(), List.of());
    }

    /** For the product page and the edit form: includes gallery photos and specifications. */
    public static ProductResponse detailed(Product p) {
        return build(p, p.getImages().stream().map(ProductImage::getImageUrl).toList(),
                p.getSpecifications().stream().map(s -> new SpecificationDto(s.getName(), s.getValue())).toList());
    }

    private static ProductResponse build(Product p, List<String> gallery, List<SpecificationDto> specs) {
        return new ProductResponse(p.getProductId(), p.getProductName(), p.getDescription(), p.getSku(),
                p.getBrand(), p.getPrice(), p.getDiscountPercent(), p.getSellingPrice(), p.getStockQuantity(),
                p.getStockQuantity() > 0, p.getImageUrl(), gallery, specs, p.getRatingAverage(), p.getRatingCount(),
                p.getSoldCount(), p.isActive(), p.getApprovalStatus(), p.getRejectionReason(),
                p.getCategory().getCategoryId(), p.getCategory().getCategoryName(), p.getCategory().isStatus(),
                p.getSeller().getId(), p.getSeller().getStoreName(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
