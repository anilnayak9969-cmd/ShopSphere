package com.example.ecommerce.dtos.review;

import java.math.BigDecimal;
import java.util.Map;

/** distribution maps star value (5..1) to the number of reviews with that rating. */
public record ReviewSummary(BigDecimal average, long count, Map<Integer, Long> distribution) {
}
