package com.example.ecommerce.dtos.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The seller sees this reason so they can fix the listing and resubmit. */
public record RejectProductRequest(
        @NotBlank(message = "Give the seller a reason") @Size(max = 255) String reason) {
}
