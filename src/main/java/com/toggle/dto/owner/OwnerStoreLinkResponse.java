package com.toggle.dto.owner;

public record OwnerStoreLinkResponse(
    Long linkId,
    Long ownerUserId,
    Long storeId,
    String storeName,
    String matchStatus,
    int matchScore,
    String matchReason
) {
}
