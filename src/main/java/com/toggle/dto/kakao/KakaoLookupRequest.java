package com.toggle.dto.kakao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record KakaoLookupRequest(
    @NotEmpty List<@Valid KakaoLookupItemRequest> items
) {
    public record KakaoLookupItemRequest(
        @NotBlank String externalPlaceId,
        @NotBlank String name,
        String address,
        Double latitude,
        Double longitude,
        String categoryName
    ) {
    }
}
