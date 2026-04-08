package com.toggle.dto.owner;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerApplicationRequest(
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    @Size(min = 2, max = 60)
    String businessName,

    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    String businessNumber,

    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    String businessAddress
) {
}
