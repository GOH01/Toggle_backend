package com.toggle.controller;

import com.toggle.dto.publicinstitution.PublicInstitutionLookupRequest;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.PublicInstitutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public-institutions")
public class PublicInstitutionController {

    private final PublicInstitutionService publicInstitutionService;

    public PublicInstitutionController(PublicInstitutionService publicInstitutionService) {
        this.publicInstitutionService = publicInstitutionService;
    }

    @PostMapping("/lookup")
    public ApiResponse<PublicInstitutionLookupResponse> lookupInstitutions(@Valid @RequestBody PublicInstitutionLookupRequest request) {
        return ApiResponse.ok(publicInstitutionService.lookupInstitutions(request));
    }
}
