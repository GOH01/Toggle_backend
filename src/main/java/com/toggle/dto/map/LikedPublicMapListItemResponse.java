package com.toggle.dto.map;

import java.time.LocalDateTime;

public record LikedPublicMapListItemResponse(
    Long mapId,
    String publicMapUuid,
    String nickname,
    String title,
    String description,
    String profileImageUrl,
    long likeCount,
    LocalDateTime likedAt
) {
}
