package com.toggle.controller;

import com.toggle.dto.store.ResolveStoreRequest;
import com.toggle.dto.store.ResolveStoreResponse;
import com.toggle.dto.store.StoreLookupRequest;
import com.toggle.dto.store.StoreLookupResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.StoreService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping("/resolve")
    public ApiResponse<ResolveStoreResponse> resolveStore(@Valid @RequestBody ResolveStoreRequest request) {
        return ApiResponse.ok(storeService.resolveRegisteredStore(request));
    }

    @PostMapping("/lookup")
    public ApiResponse<StoreLookupResponse> lookupStores(@Valid @RequestBody StoreLookupRequest request) {
        return ApiResponse.ok(storeService.lookupStores(request));
    }

    @GetMapping
    public ApiResponse<StoreLookupResponse> getStoresByIds(@RequestParam List<Long> ids) {
        return ApiResponse.ok(storeService.getStoresByIds(ids));
    }

    @GetMapping("/nearby")
    public ApiResponse<StoreLookupResponse> getNearbyStores(
        @RequestParam double latitude,
        @RequestParam double longitude,
        @RequestParam(defaultValue = "2000") int radiusMeters,
        @RequestParam(defaultValue = "30") int limit
    ) {
        return ApiResponse.ok(storeService.getNearbyVerifiedStores(latitude, longitude, radiusMeters, limit));
    }
}
