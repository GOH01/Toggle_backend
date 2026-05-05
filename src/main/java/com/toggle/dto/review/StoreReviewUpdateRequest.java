package com.toggle.dto.review;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StoreReviewUpdateRequest(
    @Min(1)
    @Max(5)
    int rating,
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    String content,
    @Size(max = 5)
    List<@NotBlank String> imageUrls
) {
}
