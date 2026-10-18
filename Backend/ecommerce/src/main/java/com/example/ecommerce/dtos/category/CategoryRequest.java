package com.example.ecommerce.dtos.category;

import com.example.ecommerce.dtos.common.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(min = 2, max = 100, message = "Category name must be 2-100 characters")
        @Pattern(regexp = "^[\\p{L}0-9][\\p{L}0-9 &,'()./-]*$",
                message = "Use letters, numbers, spaces and & , ' ( ) . / - only")
        String categoryName,

        @NotBlank(message = "Description is required")
        @Size(min = 5, max = 300, message = "Description must be 5-300 characters")
        String description,

        @Size(max = 500, message = "Image URL must be at most 500 characters")
        @Pattern(regexp = ValidationPatterns.HTTP_URL, message = ValidationPatterns.IMAGE_URL_MESSAGE)
        String imageUrl,

        /* Optional: makes this a subcategory. Null = top-level category. */
        Integer parentCategoryId) {
}
