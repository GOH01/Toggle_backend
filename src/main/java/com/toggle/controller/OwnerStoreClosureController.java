package com.toggle.controller;

import com.toggle.dto.store.StoreClosureRequestCreateRequest;
import com.toggle.dto.store.StoreClosureRequestResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.StoreClosureRequestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/stores")
public class OwnerStoreClosureController {

    private final StoreClosureRequestService storeClosureRequestService;
    private final AuthService authService;

    public OwnerStoreClosureController(StoreClosureRequestService storeClosureRequestService, AuthService authService) {
        this.storeClosureRequestService = storeClosureRequestService;
        this.authService = authService;
    }

    @PostMapping("/{storeId}/closure-requests")
    public ApiResponse<StoreClosureRequestResponse> createClosureRequest(
        @PathVariable Long storeId,
        @Valid @RequestBody StoreClosureRequestCreateRequest request
    ) {
        return ApiResponse.ok(storeClosureRequestService.createRequest(authService.getAuthenticatedUser(), storeId, request));
    }

    @GetMapping("/{storeId}/closure-requests/latest")
    public ApiResponse<StoreClosureRequestResponse> getLatestClosureRequest(@PathVariable Long storeId) {
        return ApiResponse.ok(storeClosureRequestService.getLatestRequest(authService.getAuthenticatedUser(), storeId));
    }
}
