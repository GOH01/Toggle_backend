package com.toggle.dto.review;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StoreReviewCreateRequest(
    @Min(1)
    @Max(5)
    int rating,
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    String content
) {
}
