package com.toggle.dto.favorite;

import java.util.List;

public record FavoriteStoreListResponse(
    List<FavoriteStoreListItemResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
