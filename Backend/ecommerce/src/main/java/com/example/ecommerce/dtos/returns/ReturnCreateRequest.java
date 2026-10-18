package com.example.ecommerce.dtos.returns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReturnCreateRequest(
        @NotNull(message = "Choose the item to return") Long orderItemId,
        @NotBlank(message = "Choose a reason")
        @Pattern(regexp = "^(DAMAGED|WRONG_ITEM|NOT_AS_DESCRIBED|SIZE_OR_FIT|NO_LONGER_NEEDED|OTHER)$",
                message = "Choose a valid reason") String reason,
        @Size(max = 1000, message = "Comments must be at most 1000 characters") String comments) {
}
