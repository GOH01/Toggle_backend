package com.toggle.controller;

import com.toggle.dto.owner.OwnerApplicationRequest;
import com.toggle.dto.owner.OwnerApplicationResponse;
import com.toggle.dto.owner.OwnerApplicationSummaryResponse;
import com.toggle.dto.owner.OwnerLinkedStoreResponse;
import com.toggle.dto.owner.OwnerStoreStatusResponse;
import com.toggle.dto.owner.OwnerStoreStatusUpdateRequest;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.OwnerApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/owner")
public class OwnerApplicationController {

    private final OwnerApplicationService ownerApplicationService;
    private final AuthService authService;

    public OwnerApplicationController(OwnerApplicationService ownerApplicationService, AuthService authService) {
        this.ownerApplicationService = ownerApplicationService;
        this.authService = authService;
    }

    @PostMapping(value = "/store-applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OwnerApplicationResponse> createApplication(
        @Valid @RequestPart("request") OwnerApplicationRequest request,
        @RequestPart("businessLicenseFile") MultipartFile businessLicenseFile
    ) {
        return ApiResponse.ok(ownerApplicationService.createApplication(authService.getAuthenticatedUser(), request, businessLicenseFile));
    }

    @GetMapping("/store-applications")
    public ApiResponse<List<OwnerApplicationSummaryResponse>> listMyApplications() {
        return ApiResponse.ok(ownerApplicationService.listMyApplications(authService.getAuthenticatedUser().getId()));
    }

    @GetMapping("/stores")
    public ApiResponse<List<OwnerLinkedStoreResponse>> listMyStores() {
        return ApiResponse.ok(ownerApplicationService.listLinkedStores(authService.getAuthenticatedUser().getId()));
    }

    @PostMapping("/stores/{storeId}/status")
    public ApiResponse<OwnerStoreStatusResponse> updateMyStoreStatus(
        @PathVariable Long storeId,
        @Valid @RequestBody OwnerStoreStatusUpdateRequest request
    ) {
        return ApiResponse.ok(ownerApplicationService.updateOwnerStoreStatus(authService.getAuthenticatedUser(), storeId, request));
    }
}
