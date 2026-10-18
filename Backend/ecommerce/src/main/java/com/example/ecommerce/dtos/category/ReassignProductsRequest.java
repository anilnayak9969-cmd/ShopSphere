package com.example.ecommerce.dtos.category;

import jakarta.validation.constraints.NotNull;

public record ReassignProductsRequest(
        @NotNull(message = "Choose the category to move products into") Integer targetCategoryId) {
}
