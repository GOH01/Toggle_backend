package com.toggle.dto.map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserMapUpsertRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 1000) String description,
    Boolean isPublic,
    @Size(max = 100000) String profileImageUrl
) {
}
