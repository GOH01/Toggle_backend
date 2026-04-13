package com.toggle.dto.owner;

import jakarta.validation.constraints.Size;
import java.util.List;

public record OwnerStoreProfileUpdateRequest(
    @Size(max = 500) String ownerNotice,
    String openTime,
    String closeTime,
    String breakStart,
    String breakEnd,
    List<@Size(max = 1_000_000) String> imageUrls
) {
}
