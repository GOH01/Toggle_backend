package com.toggle.dto.favorite;

import java.time.LocalDateTime;

public record FavoriteStoreListItemResponse(
    Long storeId,
    String externalPlaceId,
    String name,
    String address,
    String phone,
    String businessStatus,
    Double latitude,
    Double longitude,
    LocalDateTime favoritedAt
) {
}
