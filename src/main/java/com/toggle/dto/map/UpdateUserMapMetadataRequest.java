package com.toggle.dto.map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserMapMetadataRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 1000) String description
) {
}
