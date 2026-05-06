package com.toggle.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageUrlMapperTest {

    @Test
    void toBrowserUrlShouldConvertS3UrlToFileViewPath() {
        assertThat(ImageUrlMapper.toBrowserUrl("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/review/test.png"))
            .isEqualTo("/api/v1/files/view?key=review%2Ftest.png");
    }

    @Test
    void toBrowserUrlShouldKeepExistingViewPathAndAbsoluteUrls() {
        assertThat(ImageUrlMapper.toBrowserUrl("/api/v1/files/view?key=review%2Ftest.png"))
            .isEqualTo("/api/v1/files/view?key=review%2Ftest.png");
        assertThat(ImageUrlMapper.toBrowserUrl("https://cdn.example.com/image.png"))
            .isEqualTo("https://cdn.example.com/image.png");
    }

    @Test
    void toBrowserUrlsShouldFilterBlankValues() {
        assertThat(ImageUrlMapper.toBrowserUrls(Arrays.asList("  ", null, "https://sku-toggle.s3.ap-northeast-2.amazonaws.com/store/1.png")))
            .containsExactly("/api/v1/files/view?key=store%2F1.png");
    }
}
