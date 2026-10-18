package com.example.ecommerce.dtos.product;

import java.math.BigDecimal;
import java.util.List;

import com.example.ecommerce.dtos.common.ValidationPatterns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "Product name is required")
        @Size(min = 2, max = 150, message = "Product name must be 2-150 characters")
        String productName,

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 2000, message = "Description must be 10-2000 characters")
        String description,

        @NotBlank(message = "SKU is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,60}$", message = "SKU must be 3-60 letters, numbers, - or _")
        String sku,

        @Size(max = 80, message = "Brand must be at most 80 characters")
        String brand,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "1.00", message = "Price must be at least ₹1")
        @DecimalMax(value = "10000000.00", message = "Price is too high")
        @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimal places")
        BigDecimal price,

        @NotNull(message = "Discount is required")
        @Min(value = 0, message = "Discount cannot be negative")
        @Max(value = 90, message = "Discount cannot exceed 90%")
        Integer discountPercent,

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock cannot be negative")
        @Max(value = 100000, message = "Stock cannot exceed 100000")
        Integer stockQuantity,

        @Size(max = 500, message = "Image URL must be at most 500 characters")
        @Pattern(regexp = ValidationPatterns.HTTP_URL, message = ValidationPatterns.IMAGE_URL_MESSAGE)
        String imageUrl,

        @NotNull(message = "Category is required")
        Integer categoryId,

        /* Up to 5 extra photos shown in the product gallery. */
        @Size(max = 5, message = "You can add at most 5 gallery images")
        List<@Size(max = 500, message = "Image URL must be at most 500 characters")
             @Pattern(regexp = ValidationPatterns.HTTP_URL, message = ValidationPatterns.IMAGE_URL_MESSAGE)
             String> galleryImageUrls,

        /* Specification table rows, e.g. {"name": "Battery life", "value": "30 hours"}. */
        @Size(max = 20, message = "You can add at most 20 specifications")
        List<@Valid SpecificationDto> specifications) {
}
