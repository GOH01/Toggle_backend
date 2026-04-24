package com.toggle.dto.review;

import java.time.LocalDateTime;

public record StoreReviewItemResponse(
    Long reviewId,
    Long storeId,
    Long userId,
    String displayName,
    int rating,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
