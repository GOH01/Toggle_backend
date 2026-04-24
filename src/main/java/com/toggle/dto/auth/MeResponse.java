package com.toggle.dto.auth;

import java.util.List;

public record MeResponse(
    Long id,
    String email,
    String nickname,
    String displayName,
    String role,
    String status,
    Favorites favorites,
    MapProfile mapProfile
) {
    public record Favorites(
        List<Long> stores,
        List<Long> publics
    ) {
    }

    public record MapProfile(
        String publicMapUuid,
        boolean isPublic,
        String title,
        String description,
        String profileImageUrl
    ) {
    }
}
