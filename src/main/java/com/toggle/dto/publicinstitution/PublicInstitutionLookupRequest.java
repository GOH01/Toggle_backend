package com.toggle.dto.publicinstitution;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PublicInstitutionLookupRequest(
    @NotEmpty
    String externalSource,
    @NotEmpty
    List<PublicInstitutionLookupItemRequest> items
) {
    public record PublicInstitutionLookupItemRequest(
        @NotEmpty
        String externalPlaceId,
        String name,
        String address,
        Double latitude,
        Double longitude
    ) {
    }
}
