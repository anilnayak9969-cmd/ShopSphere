package com.example.ecommerce.dtos.product;

import jakarta.validation.constraints.NotNull;

/** An admin moves one product into another (active) category. */
public record ProductCategoryRequest(
        @NotNull(message = "Choose a category") Integer categoryId) {
}
