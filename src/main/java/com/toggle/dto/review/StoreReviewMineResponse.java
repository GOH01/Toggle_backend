package com.toggle.dto.review;

import java.util.List;

public record StoreReviewMineResponse(
    List<StoreReviewItemResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    StoreReviewSummaryResponse summary
) {
}
