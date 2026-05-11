package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.toggle.dto.kakao.KakaoAddressSearchResponse;
import com.toggle.dto.kakao.KakaoKeywordSearchRequest;
import com.toggle.dto.kakao.KakaoMapVerificationResult;
import com.toggle.dto.kakao.KakaoPlaceSearchResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class KakaoMapServiceTest {

    @Mock
    private KakaoPlaceClient kakaoPlaceClient;

    private KakaoMapService kakaoMapService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        kakaoMapService = new KakaoMapService(kakaoPlaceClient, new AddressNormalizer());
    }

    @Test
    void verifyShouldSearchAddressThenKeywordWithCoordinates() {
        given(kakaoPlaceClient.searchByAddress("경기 안양시 만안구 안양로 96"))
            .willReturn(new KakaoAddressSearchResponse(List.of(
                new KakaoAddressSearchResponse.KakaoAddressDocument(
                    "address-1",
                    "쿠니라멘 안양점",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    null,
                    null,
                    new BigDecimal("126.1234567"),
                    new BigDecimal("37.1234567")
                )
            )));
        given(kakaoPlaceClient.searchKeyword(any(KakaoKeywordSearchRequest.class)))
            .willReturn(new KakaoPlaceSearchResponse(
                new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(0, 0, true, null),
                List.of()
            ));

        ArgumentCaptor<KakaoKeywordSearchRequest> requestCaptor = ArgumentCaptor.forClass(KakaoKeywordSearchRequest.class);

        kakaoMapService.verify("쿠니라멘", " 경기 안양시 만안구 안양로 96 ");

        verify(kakaoPlaceClient).searchKeyword(requestCaptor.capture());
        KakaoKeywordSearchRequest request = requestCaptor.getValue();
        assertThat(request.query()).isEqualTo("쿠니라멘");
        assertThat(request.latitude()).isEqualTo(37.1234567);
        assertThat(request.longitude()).isEqualTo(126.1234567);
        assertThat(request.radiusMeters()).isEqualTo(300);
        assertThat(request.sort()).isEqualTo("distance");
    }

    @Test
    void verifyShouldMatchPlaceNameContainingQuery() {
        given(kakaoPlaceClient.searchByAddress(anyString()))
            .willReturn(new KakaoAddressSearchResponse(List.of(
                new KakaoAddressSearchResponse.KakaoAddressDocument(
                    "address-1",
                    "쿠니라멘",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    null,
                    null,
                    new BigDecimal("126.1234567"),
                    new BigDecimal("37.1234567")
                )
            )));
        given(kakaoPlaceClient.searchKeyword(any(KakaoKeywordSearchRequest.class)))
            .willReturn(new KakaoPlaceSearchResponse(
                new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(2, 2, true, null),
                List.of(
                    place(
                    "place-1",
                    "쿠니라멘 안양점",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    "031-123-4567",
                    "음식점 > 일식",
                    new BigDecimal("126.1234567"),
                    new BigDecimal("37.1234567")
                    ),
                    place(
                    "place-2",
                    "완전히다른가게",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    "031-000-0000",
                    "음식점 > 한식",
                    new BigDecimal("126.1234569"),
                    new BigDecimal("37.1234569")
                    )
                )
            ));

        KakaoMapVerificationResult result = kakaoMapService.verify("쿠니라멘", "경기 안양시 만안구 안양로 96");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.selectedMatch()).isNotNull();
        assertThat(result.selectedMatch().placeName()).isEqualTo("쿠니라멘 안양점");
    }

    @Test
    void verifyShouldNormalizeSuffixesBeforeMatching() {
        given(kakaoPlaceClient.searchByAddress(anyString()))
            .willReturn(new KakaoAddressSearchResponse(List.of(
                new KakaoAddressSearchResponse.KakaoAddressDocument(
                    "address-1",
                    "BBQ 평촌점",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    null,
                    null,
                    new BigDecimal("126.1234567"),
                    new BigDecimal("37.1234567")
                )
            )));
        given(kakaoPlaceClient.searchKeyword(any(KakaoKeywordSearchRequest.class)))
            .willReturn(new KakaoPlaceSearchResponse(
                new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(1, 1, true, null),
                List.of(
                    place(
                    "place-1",
                    "BBQ 평촌점",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    "031-123-4567",
                    "음식점 > 치킨",
                    new BigDecimal("126.1234567"),
                    new BigDecimal("37.1234567")
                    )
                )
            ));

        KakaoMapVerificationResult result = kakaoMapService.verify("BBQ", "경기 안양시 만안구 안양로 96");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.selectedMatch().placeName()).isEqualTo("BBQ 평촌점");
    }

    @Test
    void verifyShouldPickClosestCandidateWhenNamesTie() {
        given(kakaoPlaceClient.searchByAddress(anyString()))
            .willReturn(new KakaoAddressSearchResponse(List.of(
                new KakaoAddressSearchResponse.KakaoAddressDocument(
                    "address-1",
                    "BBQ",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    null,
                    null,
                    new BigDecimal("126.1234567"),
                    new BigDecimal("37.1234567")
                )
            )));
        given(kakaoPlaceClient.searchKeyword(any(KakaoKeywordSearchRequest.class)))
            .willReturn(new KakaoPlaceSearchResponse(
                new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(2, 2, true, null),
                List.of(
                    place(
                    "close-place",
                    "BBQ 안양점",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    "031-123-4567",
                    "음식점 > 치킨",
                    new BigDecimal("126.1234600"),
                    new BigDecimal("37.1234600")
                    ),
                    place(
                    "far-place",
                    "BBQ 평촌점",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    "031-222-2222",
                    "음식점 > 치킨",
                    new BigDecimal("126.3234600"),
                    new BigDecimal("37.3234600")
                    )
                )
            ));

        KakaoMapVerificationResult result = kakaoMapService.verify("BBQ", "경기 안양시 만안구 안양로 96");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.selectedMatch().externalPlaceId()).isEqualTo("close-place");
    }

    @Test
    void verifyShouldReturnManualReviewRequiredWhenNoKeywordCandidateMatches() {
        given(kakaoPlaceClient.searchByAddress(anyString()))
            .willReturn(new KakaoAddressSearchResponse(List.of(
                new KakaoAddressSearchResponse.KakaoAddressDocument(
                    "address-1",
                    "BBQ",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    null,
                    null,
                    new BigDecimal("126.1234567"),
                    new BigDecimal("37.1234567")
                )
            )));
        given(kakaoPlaceClient.searchKeyword(any(KakaoKeywordSearchRequest.class)))
            .willReturn(new KakaoPlaceSearchResponse(
                new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(1, 1, true, null),
                List.of(
                    place(
                    "noise-1",
                    "스타벅스 안양역점",
                    "경기 안양시 만안구 안양로 96",
                    "경기 안양시 만안구 안양로 96",
                    "031-000-0000",
                    "음식점 > 카페",
                    new BigDecimal("126.1234567"),
                    new BigDecimal("37.1234567")
                    )
                )
            ));

        KakaoMapVerificationResult result = kakaoMapService.verify("BBQ", "경기 안양시 만안구 안양로 96");

        assertThat(result.isManualReviewRequired()).isTrue();
        assertThat(result.failureCode()).isEqualTo("KAKAO_NO_MATCHED_PLACE");
    }

    @Test
    void verifyShouldReturnAddressFailureWhenNoAddressDocuments() {
        given(kakaoPlaceClient.searchByAddress(anyString()))
            .willReturn(new KakaoAddressSearchResponse(List.of()));

        KakaoMapVerificationResult result = kakaoMapService.verify("BBQ", "경기 안양시 만안구 안양로 96");

        assertThat(result.isAddressFailure()).isTrue();
        assertThat(result.failureCode()).isEqualTo("KAKAO_NO_DOCUMENTS");
    }

    private KakaoPlaceSearchResponse.KakaoPlaceDocument place(
        String id,
        String placeName,
        String roadAddress,
        String jibunAddress,
        String phone,
        String categoryName,
        BigDecimal longitude,
        BigDecimal latitude
    ) {
        return new KakaoPlaceSearchResponse.KakaoPlaceDocument(
            id,
            placeName,
            categoryName,
            "FD6",
            "음식점",
            phone,
            jibunAddress,
            roadAddress,
            longitude.toPlainString(),
            latitude.toPlainString(),
            "http://place.map.kakao.com/" + id,
            "0"
        );
    }
}
