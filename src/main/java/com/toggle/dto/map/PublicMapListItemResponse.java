package com.toggle.dto.map;

public record PublicMapListItemResponse(
    Long mapId,
    String publicMapUuid,
    String nickname,
    String title,
    String description,
    String profileImageUrl,
    long likeCount
) {
}
