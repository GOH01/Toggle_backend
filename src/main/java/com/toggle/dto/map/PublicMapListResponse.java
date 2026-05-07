package com.toggle.dto.map;

import java.util.List;

public record PublicMapListResponse(
    List<PublicMapListItemResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
