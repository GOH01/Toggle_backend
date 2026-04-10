package com.toggle.dto.owner;

import java.time.LocalDateTime;

public record MapVerificationHistoryResponse(
    String queryText,
    String status,
    String selectedPlaceName,
    String selectedRoadAddress,
    String selectedExternalPlaceId,
    String failureMessage,
    LocalDateTime verifiedAt
) {
}
