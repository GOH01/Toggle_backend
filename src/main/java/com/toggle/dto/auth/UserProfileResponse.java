package com.toggle.dto.auth;

public record UserProfileResponse(
    Long userId,
    String nickname,
    String profileImageUrl
) {
}
