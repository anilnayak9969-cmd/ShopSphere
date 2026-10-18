package com.example.ecommerce.dtos.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecificationDto(
        @NotBlank(message = "Specification name is required")
        @Size(max = 60, message = "Specification name must be at most 60 characters") String name,
        @NotBlank(message = "Specification value is required")
        @Size(max = 255, message = "Specification value must be at most 255 characters") String value) {
}
