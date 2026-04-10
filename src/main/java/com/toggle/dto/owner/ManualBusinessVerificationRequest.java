package com.toggle.dto.owner;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualBusinessVerificationRequest(
    boolean verified,
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    @Size(max = 500)
    String reason
) {
}
