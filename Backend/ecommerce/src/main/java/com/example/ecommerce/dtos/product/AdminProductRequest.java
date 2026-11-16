package com.example.ecommerce.dtos.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Admin "Add a new product" form: the usual product fields plus the store (seller) that will sell
 * and ship it. Products added by an admin are approved straight away.
 */
public record AdminProductRequest(
        @NotNull(message = "Choose the store that sells this product")
        Long sellerId,

        @NotNull(message = "Product details are required")
        @Valid
        ProductRequest product) {
}
