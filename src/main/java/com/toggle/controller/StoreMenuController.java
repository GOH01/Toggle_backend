package com.toggle.controller;

import com.toggle.dto.store.StoreMenuResponse;
import com.toggle.dto.store.StoreMenuUpsertRequest;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.StoreMenuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StoreMenuController {

    private final StoreMenuService storeMenuService;
    private final AuthService authService;

    public StoreMenuController(StoreMenuService storeMenuService, AuthService authService) {
        this.storeMenuService = storeMenuService;
        this.authService = authService;
    }

    @GetMapping("/stores/{storeId}/menus")
    public ApiResponse<StoreMenuResponse> getStoreMenus(@PathVariable Long storeId) {
        return ApiResponse.ok(storeMenuService.getStoreMenus(storeId));
    }

    @GetMapping("/owner/stores/{storeId}/menus")
    public ApiResponse<StoreMenuResponse> getMyStoreMenus(@PathVariable Long storeId) {
        return ApiResponse.ok(storeMenuService.getMyStoreMenus(storeId, authService.getAuthenticatedUser()));
    }

    @PutMapping("/owner/stores/{storeId}/menus")
    public ApiResponse<StoreMenuResponse> replaceMyStoreMenus(
        @PathVariable Long storeId,
        @Valid @RequestBody StoreMenuUpsertRequest request
    ) {
        return ApiResponse.ok(storeMenuService.replaceMyStoreMenus(storeId, authService.getAuthenticatedUser(), request));
    }
}
