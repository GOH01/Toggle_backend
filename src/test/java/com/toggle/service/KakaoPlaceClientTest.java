package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.toggle.dto.kakao.KakaoAddressSearchResponse;
import com.toggle.dto.kakao.KakaoCategorySearchRequest;
import com.toggle.dto.kakao.KakaoKeywordSearchRequest;
import com.toggle.dto.kakao.KakaoPlaceSearchResponse;
import com.toggle.global.exception.ApiException;
import java.nio.charset.StandardCharsets;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

class KakaoPlaceClientTest {

    @Test
    void searchKeywordShouldReturnMetaAndDocuments() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        KakaoPlaceSearchResponse responseBody = new KakaoPlaceSearchResponse(
            new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(2, 2, true, null),
            java.util.List.of(
                new KakaoPlaceSearchResponse.KakaoPlaceDocument(
                    "1",
                    "테스트 카페",
                    "음식점 > 카페",
                    "CE7",
                    "카페",
                    "02-123-4567",
                    "서울특별시 강남구 테헤란로 1",
                    "서울특별시 강남구 테헤란로 1",
                    "127.0000000",
                    "37.0000000",
                    "http://place.map.kakao.com/1",
                    "15"
                )
            )
        );

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
            .thenReturn(ResponseEntity.ok(responseBody));

        KakaoPlaceClient client = new KakaoPlaceClient(restTemplate);

        KakaoPlaceSearchResponse response = client.searchKeyword(
            new KakaoKeywordSearchRequest("테스트 카페", "CE7", 37.0, 127.0, 2000, 1, 15, "distance")
        );

        assertThat(response.meta().total_count()).isEqualTo(2);
        assertThat(response.documents()).hasSize(1);
        assertThat(response.documents().get(0).place_name()).isEqualTo("테스트 카페");
    }

    @Test
    void searchByAddressShouldCallAddressEndpointWithEncodedQuery() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        KakaoAddressSearchResponse responseBody = new KakaoAddressSearchResponse(
            java.util.List.of(
                new KakaoAddressSearchResponse.KakaoAddressDocument(
                    "address-1",
                    "테스트 매장",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    null,
                    null,
                    new java.math.BigDecimal("126.1234567"),
                    new java.math.BigDecimal("37.1234567")
                )
            )
        );

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
            .thenReturn(ResponseEntity.ok(responseBody));

        KakaoPlaceClient client = new KakaoPlaceClient(restTemplate);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

        KakaoAddressSearchResponse response = client.searchByAddress(" 경기 안양시 만안구 안양로 96 ");

        verify(restTemplate).exchange(urlCaptor.capture(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
        assertThat(urlCaptor.getValue()).contains("/v2/local/search/address.json");
        assertThat(urlCaptor.getValue()).doesNotContain(" 경기 안양시 만안구 안양로 96 ");
        assertThat(urlCaptor.getValue()).contains("query=");
        assertThat(response.documents()).hasSize(1);
        assertThat(response.documents().get(0).address_name()).isEqualTo("경기 안양시 만안구 안양로 96");
    }

    @Test
    void searchCategoryShouldMapUpstreamErrorsToApiException() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
            .thenThrow(new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                """
                {"code":"-2","msg":"wrong parameter"}
                """.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            ));

        KakaoPlaceClient client = new KakaoPlaceClient(restTemplate);

        assertThatThrownBy(() -> client.searchCategory(
            new KakaoCategorySearchRequest("CE7", 37.0, 127.0, 2000, 1, 15, "distance")
        ))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getCode()).isEqualTo("KAKAO_LOCAL_BAD_REQUEST");
            });
    }
}
