package com.toggle.dto.store;

import java.util.List;

public record StoreLookupItemResponse(
    Long storeId,
    String externalSource,
    String externalPlaceId,
    String name,
    String categoryName,
    String address,
    String roadAddress,
    String jibunAddress,
    String phone,
    Double latitude,
    Double longitude,
    String businessStatus,
    String liveBusinessStatus,
    String liveStatusSource,
    boolean verified,
    String verifiedAt,
    String ownerNotice,
    String openTime,
    String closeTime,
    String breakStart,
    String breakEnd,
    Double rating,
    Double reviewAverageRating,
    long reviewCount,
    long favoriteCount,
    List<String> imageUrls,
    String operationalState,
    String closureRequestStatus,
    boolean menuEligible,
    boolean menuEditable,
    String menuEligibilityReason
) {
}
