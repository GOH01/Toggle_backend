package com.toggle.controller;

import com.toggle.dto.store.ResolveStoreRequest;
import com.toggle.dto.store.ResolveStoreResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.StoreService;
import jakarta.validation.Valid;
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
        return ApiResponse.ok(storeService.resolveStore(request));
    }
}
