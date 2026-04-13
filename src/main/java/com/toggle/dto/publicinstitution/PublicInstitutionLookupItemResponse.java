package com.toggle.dto.publicinstitution;

public record PublicInstitutionLookupItemResponse(
    Long id,
    String externalSource,
    String externalPlaceId,
    String name,
    String congestionLevel,
    Integer waitTime,
    String operatingHours,
    String statusUpdatedAt
) {
}
