package com.toggle.dto.kakao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record KakaoKeywordSearchRequest(
    @NotBlank String query,
    String categoryGroupCode,
    Double latitude,
    Double longitude,
    @Min(1) Integer radiusMeters,
    @Min(1) @Max(45) Integer page,
    @Min(1) @Max(30) Integer size,
    String sort
) {
}
