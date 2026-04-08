package com.toggle.dto.owner;

import java.time.LocalDateTime;

public record OwnerApplicationSummaryResponse(
    Long applicationId,
    Long ownerUserId,
    String ownerEmail,
    String ownerNickname,
    String businessName,
    String businessNumber,
    String businessAddressRaw,
    String businessLicenseOriginalName,
    String businessLicenseContentType,
    String businessLicenseStoredPath,
    String reviewStatus,
    String rejectReason,
    LocalDateTime submittedAt,
    LocalDateTime reviewedAt
) {
}
