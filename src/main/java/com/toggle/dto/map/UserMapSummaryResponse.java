package com.toggle.dto.map;

import java.time.LocalDateTime;

public record UserMapSummaryResponse(
    Long mapId,
    String publicMapUuid,
    String title,
    String description,
    String profileImageUrl,
    boolean isPublic,
    long likeCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
