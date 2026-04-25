package com.toggle.dto.store;

import java.util.List;

public record StoreMenuResponse(
    Long storeId,
    String storeName,
    String categoryName,
    boolean enabled,
    boolean editable,
    List<StoreMenuItemResponse> items,
    String operationalState,
    String closureRequestStatus,
    boolean menuEligible,
    boolean menuEditable,
    String menuEligibilityReason
) {
}
