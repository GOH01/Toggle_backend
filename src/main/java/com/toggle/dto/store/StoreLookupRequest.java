package com.toggle.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record StoreLookupRequest(
    @NotBlank String externalSource,
    @NotEmpty List<@NotBlank String> externalPlaceIds
) {
}
