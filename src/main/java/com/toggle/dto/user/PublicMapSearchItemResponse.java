package com.toggle.dto.user;

public record PublicMapSearchItemResponse(
    String publicMapUuid,
    String nickname,
    String title,
    String description,
    long savedPlaceCount,
    String profileImageUrl
) {
}
