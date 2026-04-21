package com.toggle.dto.user;

public record UpdateMyMapProfileRequest(
    Boolean isPublic,
    String title,
    String description,
    String profileImageUrl
) {
}
