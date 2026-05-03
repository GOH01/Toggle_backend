package com.toggle.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ConfigTest {

    private final S3Config config = new S3Config();

    @Test
    void s3ClientShouldBeCreatedWithConfiguredProperties() {
        S3Properties properties = new S3Properties(
            new S3Properties.Credentials("test-access-key", "test-secret-key"),
            "ap-northeast-2",
            new S3Properties.S3Bucket("sku-toggle")
        );

        S3Client client = config.s3Client(properties);
        assertThat(client).isNotNull();
    }

    @Test
    void s3PresignerShouldBeCreatedWithConfiguredProperties() {
        S3Properties properties = new S3Properties(
            new S3Properties.Credentials("test-access-key", "test-secret-key"),
            "ap-northeast-2",
            new S3Properties.S3Bucket("sku-toggle")
        );

        S3Presigner presigner = config.s3Presigner(properties);
        assertThat(presigner).isNotNull();
    }

    @Test
    void blankAwsCredentialsShouldFailFast() {
        S3Properties properties = new S3Properties(
            new S3Properties.Credentials("", "   "),
            "ap-northeast-2",
            new S3Properties.S3Bucket("sku-toggle")
        );

        assertThatThrownBy(() -> config.s3Client(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cloud.aws.credentials.access-key");
    }
}
