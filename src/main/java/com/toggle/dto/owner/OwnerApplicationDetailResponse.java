package com.toggle.dto.owner;

import java.util.List;

public record OwnerApplicationDetailResponse(
    OwnerApplicationSummaryResponse application,
    List<BusinessVerificationHistoryResponse> businessVerificationHistories,
    List<MapVerificationHistoryResponse> mapVerificationHistories
) {
}
