package com.toggle.dto.publicinstitution;

public record PublicInstitutionLookupItemResponse(
    Long id,
    String externalSource,
    String externalPlaceId,
    String name,
    String address,
    Double latitude,
    Double longitude,
    String congestionLevel,
    Integer waitTime,
    String operatingHours,
    String statusUpdatedAt
) {
}
