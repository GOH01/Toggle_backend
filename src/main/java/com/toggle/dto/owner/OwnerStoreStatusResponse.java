package com.toggle.dto.owner;

public record OwnerStoreStatusResponse(
    Long storeId,
    String storeName,
    String liveBusinessStatus,
    String statusSource,
    String comment
) {
}
