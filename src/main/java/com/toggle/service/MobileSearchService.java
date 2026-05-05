package com.toggle.service;

import com.toggle.dto.kakao.KakaoCategorySearchRequest;
import com.toggle.dto.kakao.KakaoKeywordSearchRequest;
import com.toggle.dto.kakao.KakaoLookupRequest;
import com.toggle.dto.kakao.KakaoLookupResponse;
import com.toggle.dto.kakao.KakaoNearbySearchRequest;
import com.toggle.dto.kakao.KakaoPlaceSearchResponse;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupRequest;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupResponse;
import com.toggle.dto.store.StoreLookupRequest;
import com.toggle.dto.store.StoreLookupResponse;
import com.toggle.entity.ExternalSource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobileSearchService {

    private static final String KAKAO_SOURCE = ExternalSource.KAKAO.name();

    private final KakaoPlaceClient kakaoPlaceClient;
    private final StoreService storeService;
    private final PublicInstitutionService publicInstitutionService;

    public MobileSearchService(
        KakaoPlaceClient kakaoPlaceClient,
        StoreService storeService,
        PublicInstitutionService publicInstitutionService
    ) {
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.storeService = storeService;
        this.publicInstitutionService = publicInstitutionService;
    }

    @Transactional(readOnly = true)
    public KakaoPlaceSearchResponse searchKeyword(KakaoKeywordSearchRequest request) {
        return kakaoPlaceClient.searchKeyword(request);
    }

    @Transactional(readOnly = true)
    public KakaoPlaceSearchResponse searchCategory(KakaoCategorySearchRequest request) {
        return kakaoPlaceClient.searchCategory(request);
    }

    @Transactional(readOnly = true)
    public KakaoPlaceSearchResponse searchNearby(KakaoNearbySearchRequest request) {
        return kakaoPlaceClient.searchNearby(request);
    }

    @Transactional
    public KakaoLookupResponse lookup(KakaoLookupRequest request) {
        List<String> externalPlaceIds = request.items().stream()
            .map(KakaoLookupRequest.KakaoLookupItemRequest::externalPlaceId)
            .map(String::trim)
            .distinct()
            .toList();

        StoreLookupResponse storeLookup = storeService.lookupStores(new StoreLookupRequest(KAKAO_SOURCE, externalPlaceIds));
        PublicInstitutionLookupResponse publicLookup = publicInstitutionService.lookupInstitutions(
            new PublicInstitutionLookupRequest(
                KAKAO_SOURCE,
                request.items().stream()
                    .map(item -> new PublicInstitutionLookupRequest.PublicInstitutionLookupItemRequest(
                        item.externalPlaceId(),
                        item.name(),
                        item.address(),
                        item.latitude(),
                        item.longitude()
                    ))
                    .toList()
            )
        );

        return new KakaoLookupResponse(storeLookup.stores(), publicLookup.institutions());
    }

    @Transactional(readOnly = true)
    public StoreLookupResponse searchNearbyStores(double latitude, double longitude, int radiusMeters, int limit) {
        return storeService.getNearbyVerifiedStores(latitude, longitude, radiusMeters, limit);
    }
}
