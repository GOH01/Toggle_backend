package com.toggle.dto.store;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StorePriceItemUpsertRequest(
    @NotNull @Valid List<StorePriceItemUpsertItemRequest> priceItems
) {
}
