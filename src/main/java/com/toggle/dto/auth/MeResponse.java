package com.toggle.dto.auth;

import java.util.List;

public record MeResponse(
    Long id,
    String email,
    String nickname,
    String role,
    String status,
    Favorites favorites
) {
    public record Favorites(
        List<Long> stores,
        List<Long> publics
    ) {
    }
}
