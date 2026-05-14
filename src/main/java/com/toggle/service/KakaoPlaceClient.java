package com.toggle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.kakao.KakaoCategorySearchRequest;
import com.toggle.dto.kakao.KakaoAddressSearchResponse;
import com.toggle.dto.kakao.KakaoKeywordSearchRequest;
import com.toggle.dto.kakao.KakaoKeywordSearchResponse;
import com.toggle.dto.kakao.KakaoNearbySearchRequest;
import com.toggle.dto.kakao.KakaoPlaceSearchResponse;
import com.toggle.global.config.KakaoClientProperties;
import com.toggle.global.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.toggle.global.exception.KakaoAddressSearchException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class KakaoPlaceClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceClient.class);

    private final RestTemplate kakaoRestTemplate;
    private final ObjectMapper objectMapper;
    private final KakaoClientProperties kakaoClientProperties;

    public KakaoPlaceClient(
        @Qualifier("kakaoRestTemplate") RestTemplate kakaoRestTemplate,
        ObjectMapper objectMapper,
        KakaoClientProperties kakaoClientProperties
    ) {
        this.kakaoRestTemplate = kakaoRestTemplate;
        this.objectMapper = objectMapper;
        this.kakaoClientProperties = kakaoClientProperties;
    }

    public List<KakaoKeywordSearchResponse.KakaoPlaceDocument> searchByKeyword(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        KakaoKeywordSearchResponse body = exchange(
            buildUri("/v2/local/search/keyword.json", Map.of("query", query.trim())),
            KakaoKeywordSearchResponse.class
        );

        List<KakaoKeywordSearchResponse.KakaoPlaceDocument> documents = body == null || body.documents() == null ? List.of() : body.documents();
        log.info("Kakao keyword documents size = {}", documents.size());
        return documents;
    }

    public KakaoAddressSearchResponse searchByAddress(String query) {
        if (!StringUtils.hasText(query)) {
            return new KakaoAddressSearchResponse(List.of());
        }

        Map<String, Object> queryParams = Map.of("query", query.trim());
        URI url = buildUri("/v2/local/search/address.json", queryParams);
        log.info("Kakao address request url = {}", url);
        String body = exchangeAddressRaw(
            url,
            String.class
        );
        log.info("Kakao address raw response body = {}", body);

        if (body == null || body.isBlank()) {
            return new KakaoAddressSearchResponse(List.of());
        }

        try {
            KakaoAddressSearchResponse response = objectMapper.readValue(body, KakaoAddressSearchResponse.class);
            KakaoAddressSearchResponse normalized = response == null || response.documents() == null ? new KakaoAddressSearchResponse(List.of()) : response;
            log.info("Kakao address documents size = {}", normalized.documents().size());
            return normalized;
        } catch (Exception ex) {
            throw new KakaoAddressSearchException(
                "/v2/local/search/address.json",
                query.trim(),
                HttpStatus.SERVICE_UNAVAILABLE,
                body,
                "KAKAO_RESPONSE_PARSE_FAILED",
                ex
            );
        }
    }

    public KakaoPlaceSearchResponse searchKeyword(KakaoKeywordSearchRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", request.query().trim());
        params.put("category_group_code", normalizeOptional(request.categoryGroupCode()));
        params.put("x", request.longitude());
        params.put("y", request.latitude());
        params.put("radius", request.radiusMeters());
        params.put("page", request.page());
        params.put("size", request.size());
        params.put("sort", normalizeOptional(request.sort()));

        KakaoPlaceSearchResponse response = normalizeSearchResponse(exchange(
            buildUri("/v2/local/search/keyword.json", params),
            KakaoPlaceSearchResponse.class
        ));
        log.info("Kakao keyword search response documents size = {}", response.documents() == null ? 0 : response.documents().size());
        return response;
    }

    public KakaoPlaceSearchResponse searchCategory(KakaoCategorySearchRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category_group_code", request.categoryGroupCode().trim());
        params.put("x", request.longitude());
        params.put("y", request.latitude());
        params.put("radius", request.radiusMeters());
        params.put("page", request.page());
        params.put("size", request.size());
        params.put("sort", normalizeOptional(request.sort()));

        KakaoPlaceSearchResponse response = normalizeSearchResponse(exchange(
            buildUri("/v2/local/search/category.json", params),
            KakaoPlaceSearchResponse.class
        ));
        log.info("Kakao category search response documents size = {}", response.documents() == null ? 0 : response.documents().size());
        return response;
    }

    public KakaoPlaceSearchResponse searchNearby(KakaoNearbySearchRequest request) {
        return searchCategory(new KakaoCategorySearchRequest(
            request.categoryGroupCode(),
            request.latitude(),
            request.longitude(),
            request.radiusMeters(),
            request.page(),
            request.size(),
            request.sort()
        ));
    }

    private <T> T exchange(URI url, Class<T> responseType) {
        try {
            ResponseEntity<T> response = kakaoRestTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                responseType
            );
            log.info("Kakao API response url = {}, status = {}, body = {}", url, response.getStatusCode(), safeBody(response.getBody()));
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.warn("Kakao API error url = {}, status = {}, body = {}", url, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw mapException(ex);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_LOCAL_API_ERROR", "카카오 장소 검색 API 호출에 실패했습니다.");
        }
    }

    private <T> T exchangeAddressRaw(URI url, Class<T> responseType) {
        try {
            ResponseEntity<T> response = kakaoRestTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                responseType
            );
            log.info("Kakao address API response url = {}, status = {}, body = {}", url, response.getStatusCode(), safeBody(response.getBody()));
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
            }
            log.warn("Kakao address API error url = {}, status = {}, body = {}", url, status, ex.getResponseBodyAsString(), ex);
            throw new KakaoAddressSearchException(
                url.getPath(),
                url.getQuery(),
                status,
                ex.getResponseBodyAsString(),
                mapAddressFailureReasonCode(ex),
                ex
            );
        } catch (Exception ex) {
            throw new KakaoAddressSearchException(
                url.getPath(),
                url.getQuery(),
                HttpStatus.SERVICE_UNAVAILABLE,
                null,
                "KAKAO_RESPONSE_PARSE_FAILED",
                ex
            );
        }
    }

    private URI buildUri(String path, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(kakaoClientProperties.baseUrl()).path(path);
        queryParams.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            if (value instanceof String stringValue && !StringUtils.hasText(stringValue)) {
                return;
            }
            builder.queryParam(key, value);
        });
        return builder.build().encode().toUri();
    }

    private String safeBody(Object body) {
        if (body == null) {
            return "<null>";
        }

        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            return String.valueOf(body);
        }
    }

    private KakaoPlaceSearchResponse normalizeSearchResponse(KakaoPlaceSearchResponse body) {
        if (body != null) {
            return body;
        }

        return new KakaoPlaceSearchResponse(
            new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(0, 0, true, null),
            List.of()
        );
    }

    private ApiException mapException(HttpStatusCodeException ex) {
        int statusCode = ex.getStatusCode().value();
        if (statusCode == 429) {
            return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "KAKAO_LOCAL_RATE_LIMITED", "카카오 장소 검색 요청이 너무 많습니다.");
        }

        if (ex.getStatusCode().is4xxClientError()) {
            return new ApiException(HttpStatus.BAD_REQUEST, "KAKAO_LOCAL_BAD_REQUEST", "카카오 장소 검색 요청이 올바르지 않습니다.");
        }

        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_LOCAL_API_ERROR", "카카오 장소 검색 API가 일시적으로 응답하지 않습니다.");
    }

    private String mapAddressFailureReasonCode(HttpStatusCodeException ex) {
        int statusCode = ex.getStatusCode().value();
        if (statusCode == 429) {
            return "KAKAO_TOO_MANY_REQUESTS";
        }
        if (ex.getStatusCode().is4xxClientError()) {
            return "KAKAO_BAD_REQUEST";
        }
        return "KAKAO_API_ERROR";
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
