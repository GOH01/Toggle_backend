package com.toggle.dto.owner;

import java.time.LocalDateTime;

public record OwnerApplicationResponse(
    Long applicationId,
    Long ownerUserId,
    String storeName,
    String requestStatus,
    String businessVerificationStatus,
    String mapVerificationStatus,
    LocalDateTime submittedAt
) {
}
