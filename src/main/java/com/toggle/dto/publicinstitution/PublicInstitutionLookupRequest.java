package com.toggle.dto.publicinstitution;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PublicInstitutionLookupRequest(
    @NotEmpty
    String externalSource,
    @NotEmpty
    List<String> externalPlaceIds
) {
}
