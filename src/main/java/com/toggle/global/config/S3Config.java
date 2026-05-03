package com.toggle.global.config;

import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    S3Client s3Client(S3Properties properties) {
        validateCredentials(properties);
        return S3Client.builder()
            .region(resolveRegion(properties))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    properties.credentials().accessKey().trim(),
                    properties.credentials().secretKey().trim()
                )
            ))
            .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(S3Properties properties) {
        validateCredentials(properties);
        return S3Presigner.builder()
            .region(resolveRegion(properties))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    properties.credentials().accessKey().trim(),
                    properties.credentials().secretKey().trim()
                )
            ))
            .build();
    }

    private Region resolveRegion(S3Properties properties) {
        return Region.of(requireText(properties.region(), "cloud.aws.region"));
    }

    private void validateCredentials(S3Properties properties) {
        requireText(properties.region(), "cloud.aws.region");
        requireText(properties.s3().bucket(), "cloud.aws.s3.bucket");
        requireText(properties.credentials().accessKey(), "cloud.aws.credentials.access-key");
        requireText(properties.credentials().secretKey(), "cloud.aws.credentials.secret-key");
    }

    private String requireText(String value, String propertyName) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured in application.yml");
        }
        return normalized;
    }
}
