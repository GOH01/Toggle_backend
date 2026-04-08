package com.toggle.dto.auth;

public record AuthTokenResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    AuthUserResponse user
) {
}
