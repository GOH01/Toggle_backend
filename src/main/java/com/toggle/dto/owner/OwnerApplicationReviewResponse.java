package com.toggle.dto.owner;

import java.time.LocalDateTime;

public record OwnerApplicationReviewResponse(
    Long applicationId,
    Long ownerUserId,
    String reviewStatus,
    Long linkedStoreId,
    LocalDateTime reviewedAt,
    String rejectReason
) {
}
