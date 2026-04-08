package com.toggle.dto.auth;

public record MeResponse(
    Long id,
    String email,
    String nickname,
    String role,
    String status
) {
}
