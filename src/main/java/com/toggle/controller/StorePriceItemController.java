package com.toggle.controller;

import com.toggle.dto.store.StorePriceItemResponse;
import com.toggle.dto.store.StorePriceItemUpsertRequest;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.StorePriceItemService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StorePriceItemController {

    private final StorePriceItemService storePriceItemService;
    private final AuthService authService;

    public StorePriceItemController(StorePriceItemService storePriceItemService, AuthService authService) {
        this.storePriceItemService = storePriceItemService;
        this.authService = authService;
    }

    @GetMapping("/stores/{storeId}/price-items")
    public ApiResponse<StorePriceItemResponse> getStorePriceItems(@PathVariable Long storeId) {
        return ApiResponse.ok(storePriceItemService.getStorePriceItems(storeId));
    }

    @GetMapping("/owner/stores/{storeId}/price-items")
    public ApiResponse<StorePriceItemResponse> getMyStorePriceItems(@PathVariable Long storeId) {
        return ApiResponse.ok(storePriceItemService.getMyStorePriceItems(storeId, authService.getAuthenticatedUser()));
    }

    @PutMapping("/owner/stores/{storeId}/price-items")
    public ApiResponse<StorePriceItemResponse> replaceMyStorePriceItems(
        @PathVariable Long storeId,
        @Valid @RequestBody StorePriceItemUpsertRequest request
    ) {
        return ApiResponse.ok(storePriceItemService.replaceMyStorePriceItems(storeId, authService.getAuthenticatedUser(), request));
    }
}
