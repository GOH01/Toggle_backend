package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.dto.kakao.KakaoLookupRequest;
import com.toggle.dto.kakao.KakaoLookupResponse;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupItemResponse;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupRequest;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupResponse;
import com.toggle.dto.store.StoreLookupItemResponse;
import com.toggle.dto.store.StoreLookupRequest;
import com.toggle.dto.store.StoreLookupResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MobileSearchServiceTest {

    @Test
    void lookupShouldDelegateToStoreAndPublicLookups() {
        KakaoPlaceClient kakaoPlaceClient = org.mockito.Mockito.mock(KakaoPlaceClient.class);
        StoreService storeService = org.mockito.Mockito.mock(StoreService.class);
        PublicInstitutionService publicInstitutionService = org.mockito.Mockito.mock(PublicInstitutionService.class);
        MobileSearchService service = new MobileSearchService(kakaoPlaceClient, storeService, publicInstitutionService);

        when(storeService.lookupStores(any(StoreLookupRequest.class))).thenReturn(new StoreLookupResponse(
            List.of(new StoreLookupItemResponse(
                1L,
                "KAKAO",
                "123",
                "테스트 매장",
                "카페",
                "서울특별시 강남구 테헤란로 1",
                null,
                null,
                "02-123-4567",
                37.0,
                127.0,
                "OPEN",
                "OPEN",
                "SYSTEM",
                true,
                "2026-04-27T10:00:00",
                null,
                null,
                null,
                null,
                null,
                4.8,
                4.7,
                11L,
                3L,
                List.of(),
                "ACTIVE",
                null,
                true,
                true,
                null
            ))
        ));
        when(publicInstitutionService.lookupInstitutions(any(PublicInstitutionLookupRequest.class))).thenReturn(new PublicInstitutionLookupResponse(
            List.of(new PublicInstitutionLookupItemResponse(
                2L,
                "KAKAO",
                "987",
                "테스트 공공기관",
                "서울특별시 강남구 테헤란로 2",
                37.1,
                127.1,
                "LOW",
                10,
                "09:00-18:00",
                "2026-04-27T10:00:00"
            ))
        ));

        KakaoLookupResponse response = service.lookup(new KakaoLookupRequest(List.of(
            new KakaoLookupRequest.KakaoLookupItemRequest("123", "테스트 매장", "서울특별시 강남구 테헤란로 1", 37.0, 127.0, "카페"),
            new KakaoLookupRequest.KakaoLookupItemRequest("987", "테스트 공공기관", "서울특별시 강남구 테헤란로 2", 37.1, 127.1, "공공기관")
        )));

        assertThat(response.stores()).hasSize(1);
        assertThat(response.institutions()).hasSize(1);

        ArgumentCaptor<StoreLookupRequest> storeCaptor = ArgumentCaptor.forClass(StoreLookupRequest.class);
        verify(storeService).lookupStores(storeCaptor.capture());
        assertThat(storeCaptor.getValue().externalSource()).isEqualTo("KAKAO");
        assertThat(storeCaptor.getValue().externalPlaceIds()).containsExactly("123", "987");
    }
}
