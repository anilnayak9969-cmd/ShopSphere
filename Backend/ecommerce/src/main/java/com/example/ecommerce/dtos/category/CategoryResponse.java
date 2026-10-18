package com.example.ecommerce.dtos.category;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.Category;

/**
 * productCount = every product linked directly to the category (active and inactive).
 * activeProductCount = only products currently switched on by their seller.
 * Products in subcategories are counted on the subcategory, not the parent.
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CategoryResponse from(Category c, long productCount, long activeProductCount) {
        Category parent = c.getParent();
        return new CategoryResponse(c.getCategoryId(), c.getCategoryName(), c.getDescription(), c.getImageUrl(),
                c.isStatus(), parent == null ? null : parent.getCategoryId(),
                parent == null ? null : parent.getCategoryName(), productCount, activeProductCount, c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
