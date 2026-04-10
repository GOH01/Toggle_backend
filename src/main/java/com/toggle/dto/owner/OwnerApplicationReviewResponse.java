package com.toggle.dto.owner;

import java.time.LocalDateTime;

public record OwnerApplicationReviewResponse(
    Long applicationId,
    Long ownerUserId,
    String requestStatus,
    String businessVerificationStatus,
    String mapVerificationStatus,
    Long verifiedStoreId,
    Long linkedStoreId,
    LocalDateTime reviewedAt,
    String rejectReason
) {
}
