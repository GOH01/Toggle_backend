package com.toggle.dto.auth;

public record AuthUserResponse(
    Long id,
    String email,
    String nickname,
    String displayName,
    String role,
    String status
) {
}
