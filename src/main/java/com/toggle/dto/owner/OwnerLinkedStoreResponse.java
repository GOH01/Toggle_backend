package com.toggle.dto.owner;

import java.util.List;

public record OwnerLinkedStoreResponse(
    Long linkId,
    Long storeId,
    String storeName,
    String storeAddress,
    String liveBusinessStatus,
    String ownerNotice,
    String openTime,
    String closeTime,
    String breakStart,
    String breakEnd,
    List<String> imageUrls
) {
}
