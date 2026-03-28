package com.toggle.dto.store;

public record ResolveStoreResponse(
    Long storeId,
    String externalSource,
    String externalPlaceId,
    boolean resolved
) {
}
