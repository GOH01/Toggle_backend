package com.toggle.controller;

import com.toggle.dto.store.StoreClosureRequestRejectRequest;
import com.toggle.dto.store.StoreClosureRequestResponse;
import com.toggle.entity.StoreClosureRequestStatus;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.StoreClosureRequestService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/store-closure-requests")
public class AdminStoreClosureController {

    private final StoreClosureRequestService storeClosureRequestService;
    private final AuthService authService;

    public AdminStoreClosureController(StoreClosureRequestService storeClosureRequestService, AuthService authService) {
        this.storeClosureRequestService = storeClosureRequestService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<StoreClosureRequestResponse>> listRequests(
        @RequestParam(defaultValue = "PENDING") StoreClosureRequestStatus status
    ) {
        return ApiResponse.ok(storeClosureRequestService.listRequests(authService.getAuthenticatedUser(), status));
    }

    @PostMapping("/{requestId}/approve")
    public ApiResponse<StoreClosureRequestResponse> approve(@PathVariable Long requestId) {
        return ApiResponse.ok(storeClosureRequestService.approveRequest(requestId, authService.getAuthenticatedUser()));
    }

    @PostMapping("/{requestId}/reject")
    public ApiResponse<StoreClosureRequestResponse> reject(
        @PathVariable Long requestId,
        @Valid @RequestBody StoreClosureRequestRejectRequest request
    ) {
        return ApiResponse.ok(storeClosureRequestService.rejectRequest(requestId, authService.getAuthenticatedUser(), request));
    }
}
