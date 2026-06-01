package com.toggle.dto.review;

import java.util.List;

public record StoreReviewMinePageResponse(
    List<StoreReviewItemResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
