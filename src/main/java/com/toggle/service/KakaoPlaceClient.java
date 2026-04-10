package com.toggle.service;

import com.toggle.dto.kakao.KakaoKeywordSearchResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class KakaoPlaceClient {

    private final RestTemplate kakaoRestTemplate;

    public KakaoPlaceClient(@Qualifier("kakaoRestTemplate") RestTemplate kakaoRestTemplate) {
        this.kakaoRestTemplate = kakaoRestTemplate;
    }

    public List<KakaoKeywordSearchResponse.KakaoPlaceDocument> searchByKeyword(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        String uri = UriComponentsBuilder.fromPath("/v2/local/search/keyword.json")
            .queryParam("query", query.trim())
            .build()
            .toUriString();

        ResponseEntity<KakaoKeywordSearchResponse> response = kakaoRestTemplate.exchange(
            uri,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );

        KakaoKeywordSearchResponse body = response.getBody();
        return body == null || body.documents() == null ? List.of() : body.documents();
    }
}
