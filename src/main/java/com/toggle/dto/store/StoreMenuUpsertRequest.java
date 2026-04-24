package com.toggle.dto.store;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StoreMenuUpsertRequest(
    @NotNull @Valid List<StoreMenuUpsertItemRequest> menus
) {
}
