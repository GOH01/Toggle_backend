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
import java.net.URI;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

class KakaoPlaceClientTest {

    @Test
    void searchKeywordShouldCallKeywordEndpointWithCoordinatesAndSort() {
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

        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
            .thenReturn(ResponseEntity.ok(responseBody));

        KakaoPlaceClient client = new KakaoPlaceClient(restTemplate, new ObjectMapper(), "https://dapi.kakao.com");
        ArgumentCaptor<URI> urlCaptor = ArgumentCaptor.forClass(URI.class);

        KakaoPlaceSearchResponse response = client.searchKeyword(
            new KakaoKeywordSearchRequest("테스트 카페", null, 37.1234567, 126.1234567, 300, 1, 15, "distance")
        );

        verify(restTemplate).exchange(urlCaptor.capture(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
        assertThat(urlCaptor.getValue().toString()).contains("/v2/local/search/keyword.json");
        assertThat(urlCaptor.getValue().toString()).contains("query=");
        assertThat(urlCaptor.getValue().toString()).doesNotContain("테스트 카페");
        assertThat(urlCaptor.getValue().toString()).contains("x=126.1234567");
        assertThat(urlCaptor.getValue().toString()).contains("y=37.1234567");
        assertThat(urlCaptor.getValue().toString()).contains("radius=300");
        assertThat(urlCaptor.getValue().toString()).contains("sort=distance");

        assertThat(response.meta().total_count()).isEqualTo(2);
        assertThat(response.documents()).hasSize(1);
        assertThat(response.documents().get(0).place_name()).isEqualTo("테스트 카페");
    }

    @Test
    void searchByAddressShouldCallAddressEndpointWithEncodedQuery() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        String responseBody = """
            {
              "documents": [
                {
                  "id": "address-1",
                  "place_name": "테스트 매장",
                  "address_name": "경기 안양시 만안구 안양로 96",
                  "road_address_name": "경기 안양시 만안구 안양로 96",
                  "x": "126.1234567",
                  "y": "37.1234567"
                }
              ]
            }
            """;

        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
            .thenReturn(ResponseEntity.ok(responseBody));

        KakaoPlaceClient client = new KakaoPlaceClient(restTemplate, new ObjectMapper(), "https://dapi.kakao.com");
        ArgumentCaptor<URI> urlCaptor = ArgumentCaptor.forClass(URI.class);

        KakaoAddressSearchResponse response = client.searchByAddress(" 경기 안양시 만안구 안양로 96 ");

        verify(restTemplate).exchange(urlCaptor.capture(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
        assertThat(urlCaptor.getValue().toString()).contains("/v2/local/search/address.json");
        assertThat(urlCaptor.getValue().toString()).doesNotContain(" 경기 안양시 만안구 안양로 96 ");
        assertThat(urlCaptor.getValue().toString()).contains("query=");
        assertThat(response.documents()).hasSize(1);
        assertThat(response.documents().get(0).address_name()).isEqualTo("경기 안양시 만안구 안양로 96");
    }

    @Test
    void searchCategoryShouldMapUpstreamErrorsToApiException() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
            .thenThrow(new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                """
                {"code":"-2","msg":"wrong parameter"}
                """.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            ));

        KakaoPlaceClient client = new KakaoPlaceClient(restTemplate, new ObjectMapper(), "https://dapi.kakao.com");

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
