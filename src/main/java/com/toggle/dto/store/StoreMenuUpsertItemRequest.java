package com.toggle.dto.store;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StoreMenuUpsertItemRequest(
    @NotBlank @Size(max = 120) String name,
    @NotNull @Min(0) Integer price,
    Boolean representative,
    @Size(max = 1000) String description,
    @Size(max = 100000) String imageUrl,
    Integer displayOrder,
    Boolean available
) {
}
