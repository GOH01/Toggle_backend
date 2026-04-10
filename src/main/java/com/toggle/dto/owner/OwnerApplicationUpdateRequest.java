package com.toggle.dto.owner;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record OwnerApplicationUpdateRequest(
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    @Size(min = 2, max = 80)
    String storeName,

    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    String businessNumber,

    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    @Size(min = 2, max = 60)
    String representativeName,

    @NotNull
    LocalDate businessOpenDate,

    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    String businessAddress

    ,

    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "^[0-9+()\\-\\s]{7,30}$")
    String businessPhone
) {
}
