package com.toggle.dto.store;

import java.time.LocalDateTime;

public record StoreClosureRequestResponse(
    Long requestId,
    Long storeId,
    String storeName,
    Long ownerUserId,
    String ownerNickname,
    String ownerEmail,
    String requestReason,
    String requestStatus,
    Long reviewedByUserId,
    String reviewedByNickname,
    String reviewReason,
    LocalDateTime requestedAt,
    LocalDateTime reviewedAt,
    String operationalState,
    boolean menuEligible,
    boolean menuEditable,
    String menuEligibilityReason
) {
}
