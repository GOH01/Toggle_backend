package com.toggle.dto.owner;

import java.util.List;

public record OwnerLinkedStoreResponse(
    Long linkId,
    Long ownerUserId,
    String ownerNickname,
    String ownerEmail,
    Long storeId,
    String storeName,
    String storeAddress,
    String categoryName,
    String liveBusinessStatus,
    String ownerNotice,
    String openTime,
    String closeTime,
    String breakStart,
    String breakEnd,
    List<String> imageUrls,
    String operationalState,
    String closureRequestStatus,
    boolean menuEligible,
    boolean menuEditable,
    String menuEligibilityReason,
    boolean priceItemEligible,
    boolean priceItemEditable,
    String priceItemEligibilityReason
) {
}
