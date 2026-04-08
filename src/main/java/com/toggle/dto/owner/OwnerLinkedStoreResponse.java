package com.toggle.dto.owner;

public record OwnerLinkedStoreResponse(
    Long linkId,
    Long storeId,
    String storeName,
    String storeAddress,
    String liveBusinessStatus
) {
}
