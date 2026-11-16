package com.example.ecommerce.dtos.review;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Review Moderation (admin). */
public final class ReviewModerationDtos {

    private ReviewModerationDtos() {
    }

    /** Why the review is rejected; the customer sees this reason. */
    public record RejectRequest(
            @NotBlank(message = "Give a reason for rejecting the review")
            @Size(min = 3, max = 255, message = "Reason must be 3-255 characters") String reason) {
    }

    /** Cards on the moderation page. */
    public record ModerationSummary(long pending, long approved, long rejected, long total, BigDecimal averageRating) {
    }
}
