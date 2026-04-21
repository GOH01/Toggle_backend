package com.toggle.dto.favorite;

import java.time.LocalDateTime;
import java.util.List;

public record FavoriteStoreListItemResponse(
    Long storeId,
    String externalPlaceId,
    String name,
    String categoryName,
    String address,
    String roadAddress,
    String jibunAddress,
    String phone,
    String businessStatus,
    String liveBusinessStatus,
    String liveStatusSource,
    Double latitude,
    Double longitude,
    String ownerNotice,
    String openTime,
    String closeTime,
    String breakStart,
    String breakEnd,
    Double rating,
    List<String> imageUrls,
    LocalDateTime favoritedAt
) {
}
