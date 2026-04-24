package com.toggle.dto.review;

import java.math.BigDecimal;

public record StoreReviewSummaryResponse(
    BigDecimal averageRating,
    long reviewCount
) {
}
