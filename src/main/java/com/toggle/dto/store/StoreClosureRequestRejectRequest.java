package com.toggle.dto.store;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreClosureRequestRejectRequest(
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    @Size(max = 1000)
    String reason
) {
}
