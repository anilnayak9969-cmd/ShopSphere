package com.example.ecommerce.dtos.returns;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** approve=true refunds the item and puts it back into stock; approve=false rejects the request. */
public record ReturnDecisionRequest(
        @NotNull(message = "Approve or reject the return") Boolean approve,
        @Size(max = 255, message = "Note must be at most 255 characters") String note) {
}
