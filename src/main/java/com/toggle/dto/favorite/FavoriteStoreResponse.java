package com.toggle.dto.favorite;

import java.time.LocalDateTime;

public record FavoriteStoreResponse(
    Long favoriteId,
    Long storeId,
    boolean favorited,
    LocalDateTime createdAt
) {
}
