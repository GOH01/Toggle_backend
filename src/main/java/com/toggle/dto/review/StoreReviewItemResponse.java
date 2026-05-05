package com.toggle.dto.review;

import java.time.LocalDateTime;
import java.util.List;

public record StoreReviewItemResponse(
    Long reviewId,
    Long storeId,
    Long userId,
    String displayName,
    int rating,
    String content,
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
