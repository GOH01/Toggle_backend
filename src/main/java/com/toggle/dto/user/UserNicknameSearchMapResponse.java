package com.toggle.dto.user;

import java.time.LocalDateTime;

public record UserNicknameSearchMapResponse(
    Long mapId,
    String title,
    String description,
    String mapProfileImageUrl,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
