package com.toggle.controller;

import com.toggle.dto.owner.OwnerApplicationReviewRequest;
import com.toggle.dto.owner.OwnerApplicationReviewResponse;
import com.toggle.dto.owner.OwnerApplicationSummaryResponse;
import com.toggle.dto.owner.OwnerStoreLinkResponse;
import com.toggle.dto.owner.OwnerStoreMatchCandidateResponse;
import com.toggle.dto.owner.OwnerStoreMatchRequest;
import com.toggle.global.response.ApiResponse;
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
@RequestMapping("/api/v1/admin/owner-store-applications")
public class AdminOwnerApplicationController {

    private final OwnerApplicationService ownerApplicationService;

    public AdminOwnerApplicationController(OwnerApplicationService ownerApplicationService) {
        this.ownerApplicationService = ownerApplicationService;
    }

    @GetMapping
    public ApiResponse<List<OwnerApplicationSummaryResponse>> listApplications() {
        return ApiResponse.ok(ownerApplicationService.listApplications());
    }

    @GetMapping("/{applicationId}/match-candidates")
    public ApiResponse<List<OwnerStoreMatchCandidateResponse>> listMatchCandidates(@PathVariable Long applicationId) {
        return ApiResponse.ok(ownerApplicationService.findMatchCandidates(applicationId));
    }

    @PostMapping("/{applicationId}/approve")
    public ApiResponse<OwnerApplicationReviewResponse> approve(
        @PathVariable Long applicationId,
        @Valid @RequestBody OwnerStoreMatchRequest request
    ) {
        return ApiResponse.ok(ownerApplicationService.approve(applicationId, request.storeId()));
    }

    @PostMapping("/{applicationId}/reject")
    public ApiResponse<OwnerApplicationReviewResponse> reject(
        @PathVariable Long applicationId,
        @Valid @RequestBody OwnerApplicationReviewRequest request
    ) {
        return ApiResponse.ok(ownerApplicationService.reject(applicationId, request.reason()));
    }
}
