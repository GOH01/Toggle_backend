package com.toggle.dto.owner;

import java.time.LocalDateTime;

public record MapVerificationHistoryResponse(
    String queryText,
    String status,
    Integer candidateCount,
    String selectedPlaceName,
    String selectedRoadAddress,
    String selectedJibunAddress,
    String selectedExternalPlaceId,
    String failureCode,
    String failureMessage,
    LocalDateTime verifiedAt
) {
}
