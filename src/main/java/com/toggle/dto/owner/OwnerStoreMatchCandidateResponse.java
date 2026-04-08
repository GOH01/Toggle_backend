package com.toggle.dto.owner;

import java.util.List;

public record OwnerStoreMatchCandidateResponse(
    Long storeId,
    String storeName,
    String storeAddress,
    int score,
    List<String> reasons
) {
}
