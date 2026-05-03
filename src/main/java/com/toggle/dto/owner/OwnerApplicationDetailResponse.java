package com.toggle.dto.owner;

import java.util.List;

public record OwnerApplicationDetailResponse(
    OwnerApplicationSummaryResponse application,
    String businessLicensePresignedUrl,
    List<BusinessVerificationHistoryResponse> businessVerificationHistories,
    List<MapVerificationHistoryResponse> mapVerificationHistories
) {
}
