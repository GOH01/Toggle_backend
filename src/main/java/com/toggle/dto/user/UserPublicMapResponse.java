package com.toggle.dto.user;

import java.util.List;

public record UserPublicMapResponse(
    String publicMapId,
    Long userId,
    String nickname,
    String title,
    String description,
    String profileImageUrl,
    List<Long> stores,
    List<Long> publics
) {
}
