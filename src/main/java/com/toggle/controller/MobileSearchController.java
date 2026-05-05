package com.toggle.controller;

import com.toggle.dto.kakao.KakaoCategorySearchRequest;
import com.toggle.dto.kakao.KakaoKeywordSearchRequest;
import com.toggle.dto.kakao.KakaoLookupRequest;
import com.toggle.dto.kakao.KakaoLookupResponse;
import com.toggle.dto.kakao.KakaoNearbySearchRequest;
import com.toggle.dto.kakao.KakaoPlaceSearchResponse;
import com.toggle.dto.store.StoreLookupResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.MobileSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/mobile-search")
public class MobileSearchController {

    private final MobileSearchService mobileSearchService;

    public MobileSearchController(MobileSearchService mobileSearchService) {
        this.mobileSearchService = mobileSearchService;
    }

    @GetMapping("/places/keyword")
    public ApiResponse<KakaoPlaceSearchResponse> searchKeyword(@Valid @ModelAttribute KakaoKeywordSearchRequest request) {
        return ApiResponse.ok(mobileSearchService.searchKeyword(request));
    }

    @GetMapping("/places/category")
    public ApiResponse<KakaoPlaceSearchResponse> searchCategory(@Valid @ModelAttribute KakaoCategorySearchRequest request) {
        return ApiResponse.ok(mobileSearchService.searchCategory(request));
    }

    @GetMapping("/places/nearby")
    public ApiResponse<KakaoPlaceSearchResponse> searchNearby(@Valid @ModelAttribute KakaoNearbySearchRequest request) {
        return ApiResponse.ok(mobileSearchService.searchNearby(request));
    }

    @PostMapping("/places/lookup")
    public ApiResponse<KakaoLookupResponse> lookup(@Valid @RequestBody KakaoLookupRequest request) {
        return ApiResponse.ok(mobileSearchService.lookup(request));
    }

    @GetMapping("/stores/nearby")
    public ApiResponse<StoreLookupResponse> searchNearbyStores(
        @RequestParam double latitude,
        @RequestParam double longitude,
        @RequestParam(defaultValue = "2000") @Min(1) int radiusMeters,
        @RequestParam(defaultValue = "30") @Min(1) @Max(30) int limit
    ) {
        return ApiResponse.ok(mobileSearchService.searchNearbyStores(latitude, longitude, radiusMeters, limit));
    }
}
