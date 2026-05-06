package com.toggle.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageUrlMapperTest {

    @Test
    void toObjectKeyShouldNormalizeBrowserViewUrlAndS3Url() {
        assertThat(ImageUrlMapper.toObjectKey("/api/v1/files/view?key=store%2Fhero%20image.png"))
            .isEqualTo("store/hero image.png");
        assertThat(ImageUrlMapper.toObjectKey("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/store/hero%20image.png"))
            .isEqualTo("store/hero image.png");
        assertThat(ImageUrlMapper.toObjectKey("store/hero image.png"))
            .isEqualTo("store/hero image.png");
    }

    @Test
    void toBrowserUrlShouldConvertS3UrlToFileViewPath() {
        assertThat(ImageUrlMapper.toBrowserUrl("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/review/test.png"))
            .isEqualTo("/api/v1/files/view?key=review%2Ftest.png");
    }

    @Test
    void toObjectKeysShouldDropBlankValues() {
        assertThat(ImageUrlMapper.toObjectKeys(Arrays.asList(" ", null, "/api/v1/files/view?key=review%2F1.png")))
            .containsExactly("review/1.png");
    }

    @Test
    void toBrowserUrlShouldConvertCanonicalObjectKeysToViewPaths() {
        assertThat(ImageUrlMapper.toBrowserUrl("store/hero image.png"))
            .isEqualTo("/api/v1/files/view?key=store%2Fhero+image.png");
    }
}
