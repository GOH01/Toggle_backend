package com.toggle.dto.publicinstitution;

import java.util.List;

public record PublicInstitutionLookupResponse(
    List<PublicInstitutionLookupItemResponse> institutions
) {
}
