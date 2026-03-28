package com.toggle.dto.store;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ResolveStoreRequest(
    @NotBlank String externalSource,
    @NotBlank String externalPlaceId,
    @NotBlank String name,
    @NotBlank String address,
    String phone,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
) {
}
