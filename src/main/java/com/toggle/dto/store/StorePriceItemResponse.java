package com.toggle.dto.store;

import java.util.List;

public record StorePriceItemResponse(
    Long storeId,
    String storeName,
    String categoryName,
    boolean enabled,
    boolean editable,
    List<StorePriceItemItemResponse> items,
    String operationalState,
    String closureRequestStatus,
    boolean priceItemEligible,
    boolean priceItemEditable,
    String priceItemEligibilityReason
) {
}
