package com.example.ecommerce.dtos.category;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.Category;

/**
 * Counts for products linked directly to this category:
 * productCount = every product (active and inactive);
 * activeProductCount = products currently switched on by their seller;
 * liveProductCount = products customers can actually see (switched on, admin-approved, approved seller);
 * pendingProductCount = products waiting for admin approval.
 * totalProductCount / totalLiveProductCount = the same as productCount / liveProductCount, but including every
 * subcategory below this one (so a department like "Electronics" shows what is inside it).
 */
public record CategoryResponse(
        Integer categoryId,
        String categoryName,
        String description,
        String imageUrl,
        boolean active,
        Integer parentCategoryId,
        String parentCategoryName,
        long productCount,
        long activeProductCount,
        long liveProductCount,
        long pendingProductCount,
        long totalProductCount,
        long totalLiveProductCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** counts = [total, active, live, pending, totalWithSubcategories, liveWithSubcategories]. */
    public static CategoryResponse from(Category c, long[] counts) {
        Category parent = c.getParent();
        return new CategoryResponse(c.getCategoryId(), c.getCategoryName(), c.getDescription(), c.getImageUrl(),
                c.isStatus(), parent == null ? null : parent.getCategoryId(),
                parent == null ? null : parent.getCategoryName(), counts[0], counts[1], counts[2], counts[3],
                counts[4], counts[5], c.getCreatedAt(), c.getUpdatedAt());
    }
}
