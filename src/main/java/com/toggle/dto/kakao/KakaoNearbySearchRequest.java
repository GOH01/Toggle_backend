package com.toggle.dto.kakao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KakaoNearbySearchRequest(
    @NotBlank String categoryGroupCode,
    @NotNull Double latitude,
    @NotNull Double longitude,
    @NotNull @Min(1) Integer radiusMeters,
    @Min(1) @Max(45) Integer page,
    @Min(1) @Max(30) Integer size,
    String sort
) {
}
