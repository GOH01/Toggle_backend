package com.toggle.controller;

import com.toggle.dto.owner.ExecuteMapVerificationRequest;
import com.toggle.dto.owner.ManualBusinessVerificationRequest;
import com.toggle.dto.owner.OwnerApplicationApproveRequest;
import com.toggle.dto.owner.OwnerApplicationDetailResponse;
import com.toggle.dto.owner.OwnerApplicationReviewRequest;
import com.toggle.dto.owner.OwnerApplicationReviewResponse;
import com.toggle.dto.owner.OwnerApplicationSummaryResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.OwnerApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOwnerApplicationController {

    private final OwnerApplicationService ownerApplicationService;
    private final AuthService authService;

    public AdminOwnerApplicationController(OwnerApplicationService ownerApplicationService, AuthService authService) {
        this.ownerApplicationService = ownerApplicationService;
        this.authService = authService;
    }

    @GetMapping({"/owner-store-applications", "/store-registration-requests"})
    public ApiResponse<List<OwnerApplicationSummaryResponse>> listApplications() {
        return ApiResponse.ok(ownerApplicationService.listApplications());
    }

    @GetMapping({"/owner-store-applications/{applicationId}", "/store-registration-requests/{applicationId}"})
    public ApiResponse<OwnerApplicationDetailResponse> getApplication(@PathVariable Long applicationId) {
        return ApiResponse.ok(ownerApplicationService.getApplicationDetail(applicationId));
    }

    @PostMapping({
        "/owner-store-applications/{applicationId}/business-verifications/execute",
        "/store-registration-requests/{applicationId}/business-verifications/execute"
    })
    public ApiResponse<OwnerApplicationSummaryResponse> executeBusinessVerification(@PathVariable Long applicationId) {
        return ApiResponse.ok(ownerApplicationService.executeBusinessVerification(applicationId, authService.getAuthenticatedUser()));
    }

    @PostMapping({
        "/owner-store-applications/{applicationId}/business-verifications/manual",
        "/store-registration-requests/{applicationId}/business-verifications/manual"
    })
    public ApiResponse<OwnerApplicationSummaryResponse> manualBusinessVerification(
        @PathVariable Long applicationId,
        @Valid @RequestBody ManualBusinessVerificationRequest request
    ) {
        return ApiResponse.ok(ownerApplicationService.manualVerifyBusiness(applicationId, authService.getAuthenticatedUser(), request));
    }

    @PostMapping({
        "/owner-store-applications/{applicationId}/map-verifications/execute",
        "/store-registration-requests/{applicationId}/map-verifications/execute"
    })
    public ApiResponse<OwnerApplicationSummaryResponse> executeMapVerification(
        @PathVariable Long applicationId,
        @Valid @RequestBody ExecuteMapVerificationRequest request
    ) {
        return ApiResponse.ok(ownerApplicationService.executeMapVerification(applicationId, request, authService.getAuthenticatedUser()));
    }

    @PostMapping({"/owner-store-applications/{applicationId}/approve", "/store-registration-requests/{applicationId}/approve"})
    public ApiResponse<OwnerApplicationReviewResponse> approve(
        @PathVariable Long applicationId,
        @Valid @RequestBody OwnerApplicationApproveRequest request
    ) {
        return ApiResponse.ok(ownerApplicationService.approve(applicationId, request, authService.getAuthenticatedUser()));
    }

    @PostMapping({"/owner-store-applications/{applicationId}/reject", "/store-registration-requests/{applicationId}/reject"})
    public ApiResponse<OwnerApplicationReviewResponse> reject(
        @PathVariable Long applicationId,
        @Valid @RequestBody OwnerApplicationReviewRequest request
    ) {
        return ApiResponse.ok(ownerApplicationService.reject(applicationId, request.reason(), authService.getAuthenticatedUser()));
    }
}
