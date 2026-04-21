package com.toggle.dto.user;

import java.time.LocalDateTime;

public record MyMapPlaceResponse(
    String type,
    Long placeId,
    boolean inMyMap,
    LocalDateTime updatedAt
) {
}
