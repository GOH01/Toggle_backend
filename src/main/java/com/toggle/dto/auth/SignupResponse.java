package com.toggle.dto.auth;

import java.time.LocalDateTime;

public record SignupResponse(
    Long userId,
    String email,
    String nickname,
    String role,
    String status,
    LocalDateTime createdAt
) {
}
