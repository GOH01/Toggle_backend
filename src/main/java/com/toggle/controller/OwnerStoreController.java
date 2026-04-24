package com.toggle.controller;

import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.OwnerApplicationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/stores")
public class OwnerStoreController {

    private final OwnerApplicationService ownerApplicationService;
    private final AuthService authService;

    public OwnerStoreController(OwnerApplicationService ownerApplicationService, AuthService authService) {
        this.ownerApplicationService = ownerApplicationService;
        this.authService = authService;
    }

    @DeleteMapping("/{storeId}/link")
    public ApiResponse<Void> unlinkOwnerStore(@PathVariable Long storeId) {
        ownerApplicationService.unlinkOwnerStore(authService.getAuthenticatedUser(), storeId);
        return ApiResponse.ok(null);
    }
}
