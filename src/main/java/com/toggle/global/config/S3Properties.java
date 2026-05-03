package com.toggle.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cloud.aws")
public record S3Properties(
    @Valid Credentials credentials,
    @NotBlank String region,
    @Valid S3Bucket s3
) {

    public record Credentials(
        @NotBlank String accessKey,
        @NotBlank String secretKey
    ) {
    }

    public record S3Bucket(
        @NotBlank String bucket
    ) {
    }
}
