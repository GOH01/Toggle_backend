package com.toggle.dto.owner;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record OwnerApplicationSummaryResponse(
    Long applicationId,
    Long ownerUserId,
    String ownerEmail,
    String ownerNickname,
    String storeName,
    String businessNumber,
    String representativeName,
    LocalDate businessOpenDate,
    String businessAddressRaw,
    String businessPhone,
    String businessLicenseObjectKey,
    LocalDateTime businessLicenseDeletedAt,
    String businessLicenseDeleteReason,
    String requestStatus,
    String businessVerificationStatus,
    String mapVerificationStatus,
    Long verifiedStoreId,
    String verifiedStoreName,
    String rejectReason,
    LocalDateTime submittedAt,
    LocalDateTime reviewedAt
) {
}
