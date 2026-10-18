package com.example.ecommerce.dtos.common;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull(message = "active must be true or false") Boolean active) {
}
