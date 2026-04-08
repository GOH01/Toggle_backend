package com.toggle.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.owner-documents")
public record OwnerDocumentStorageProperties(
    @NotBlank
    String uploadDir
) {
}
