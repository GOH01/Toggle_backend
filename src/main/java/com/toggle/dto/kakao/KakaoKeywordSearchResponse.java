package com.toggle.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordSearchResponse(
    List<KakaoPlaceDocument> documents
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoPlaceDocument(
        String id,
        String place_name,
        String address_name,
        String road_address_name,
        String phone,
        String category_name,
        BigDecimal x,
        BigDecimal y
    ) {
    }
}
