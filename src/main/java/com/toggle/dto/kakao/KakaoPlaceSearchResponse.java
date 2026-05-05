package com.toggle.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoPlaceSearchResponse(
    KakaoPlaceSearchMeta meta,
    List<KakaoPlaceDocument> documents
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoPlaceSearchMeta(
        Integer total_count,
        Integer pageable_count,
        Boolean is_end,
        KakaoSameName same_name
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoSameName(
        List<String> region,
        String keyword,
        String selected_region
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoPlaceDocument(
        String id,
        String place_name,
        String category_name,
        String category_group_code,
        String category_group_name,
        String phone,
        String address_name,
        String road_address_name,
        String x,
        String y,
        String place_url,
        String distance
    ) {
    }
}
