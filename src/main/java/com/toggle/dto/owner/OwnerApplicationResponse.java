package com.toggle.dto.owner;

import java.time.LocalDateTime;

public record OwnerApplicationResponse(
    Long applicationId,
    Long ownerUserId,
    String businessName,
    String reviewStatus,
    LocalDateTime submittedAt
) {
}
