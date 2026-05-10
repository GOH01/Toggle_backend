package com.toggle.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.owner.OwnerApplicationRequest;
import com.toggle.dto.owner.OwnerApplicationResponse;
import com.toggle.dto.owner.OwnerApplicationSummaryResponse;
import com.toggle.dto.owner.OwnerApplicationUpdateRequest;
import com.toggle.dto.owner.OwnerLinkedStoreResponse;
import com.toggle.dto.owner.OwnerStoreProfileUpdateRequest;
import com.toggle.dto.owner.OwnerStoreStatusResponse;
import com.toggle.dto.owner.OwnerStoreStatusUpdateRequest;
import com.toggle.global.exception.ApiException;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.OwnerApplicationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public OwnerApplicationController(
        OwnerApplicationService ownerApplicationService,
        AuthService authService,
        ObjectMapper objectMapper,
        Validator validator
    ) {
        this.ownerApplicationService = ownerApplicationService;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping(value = {"/store-applications", "/store-registration-requests"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OwnerApplicationResponse> createApplication(
        @RequestPart("request") String requestJson,
        @RequestPart("businessLicenseFile") MultipartFile businessLicenseFile
    ) {
        return ApiResponse.ok(ownerApplicationService.createApplication(
            authService.getAuthenticatedUser(),
            parseRequestPart(requestJson, OwnerApplicationRequest.class),
            businessLicenseFile
        ));
    }

    @PatchMapping(value = {"/store-applications/{applicationId}", "/store-registration-requests/{applicationId}"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OwnerApplicationResponse> updateApplication(
        @PathVariable Long applicationId,
        @RequestPart("request") String requestJson,
        @RequestPart(value = "businessLicenseFile", required = false) MultipartFile businessLicenseFile
    ) {
        return ApiResponse.ok(ownerApplicationService.updateApplication(
            authService.getAuthenticatedUser(),
            applicationId,
            parseRequestPart(requestJson, OwnerApplicationUpdateRequest.class),
            businessLicenseFile
        ));
    }

    @GetMapping({"/store-applications", "/store-registration-requests"})
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

    @PutMapping("/stores/{storeId}/profile")
    public ApiResponse<OwnerLinkedStoreResponse> updateMyStoreProfile(
        @PathVariable Long storeId,
        @Valid @RequestBody OwnerStoreProfileUpdateRequest request
    ) {
        return ApiResponse.ok(ownerApplicationService.updateOwnerStoreProfile(authService.getAuthenticatedUser(), storeId, request));
    }

    private <T> T parseRequestPart(String requestJson, Class<T> requestType) {
        try {
            T request = objectMapper.readValue(requestJson, requestType);
            Set<ConstraintViolation<T>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.");
            }
            return request;
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_JSON", "요청 JSON 형식이 올바르지 않습니다.");
        }
    }
}
