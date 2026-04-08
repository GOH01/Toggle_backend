package com.toggle.dto.owner;

import jakarta.validation.constraints.NotNull;

public record OwnerStoreMatchRequest(
    @NotNull
    Long storeId
) {
}
