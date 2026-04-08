package com.toggle.dto.store;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ResolveStoreRequest(
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank String externalSource,
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank String externalPlaceId,
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank String name,
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank String address,
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    String phone,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
) {
}
