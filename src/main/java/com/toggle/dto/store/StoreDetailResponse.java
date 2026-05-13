package com.toggle.dto.store;

import java.util.List;

public record StoreDetailResponse(
    Long storeId,
    String storeName,
    String categoryName,
    String storeType,
    List<StoreMenuItemResponse> menus,
    List<StorePriceItemItemResponse> priceItems,
    String operationalState,
    String closureRequestStatus,
    boolean menuEligible,
    boolean menuEditable,
    boolean priceItemEligible,
    boolean priceItemEditable,
    String menuEligibilityReason,
    String priceItemEligibilityReason
) {
}
