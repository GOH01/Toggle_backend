package com.toggle.dto.map;

import java.util.List;

public record LikedPublicMapListResponse(
    List<LikedPublicMapListItemResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
