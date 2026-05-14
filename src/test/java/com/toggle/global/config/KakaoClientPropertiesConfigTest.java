package com.toggle.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class KakaoClientPropertiesConfigTest {

    @Test
    void resolvesCanonicalAppKakaoKeysWhenPresent() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.kakao.api-key", "app-key")
            .withProperty("app.kakao.base-url", "https://dapi.kakao.com");

        KakaoClientProperties properties = KakaoClientProperties.resolve(environment);

        assertThat(properties.apiKey()).isEqualTo("app-key");
        assertThat(properties.baseUrl()).isEqualTo("https://dapi.kakao.com");
        assertThat(properties.apiKeySource()).isEqualTo("app.kakao.api-key");
        assertThat(properties.baseUrlSource()).isEqualTo("app.kakao.base-url");
    }

    @Test
    void fallsBackToLegacyKakaoKeysWhenCanonicalKeysAreMissing() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("kakao.api-key", "legacy-key")
            .withProperty("kakao.base-url", "https://legacy-dapi.kakao.com");

        KakaoClientProperties properties = KakaoClientProperties.resolve(environment);

        assertThat(properties.apiKey()).isEqualTo("legacy-key");
        assertThat(properties.baseUrl()).isEqualTo("https://legacy-dapi.kakao.com");
        assertThat(properties.apiKeySource()).isEqualTo("kakao.api-key");
        assertThat(properties.baseUrlSource()).isEqualTo("kakao.base-url");
    }

    @Test
    void usesDefaultBaseUrlWhenNeitherCanonicalNorLegacyBaseUrlIsPresent() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.kakao.api-key", "app-key");

        KakaoClientProperties properties = KakaoClientProperties.resolve(environment);

        assertThat(properties.baseUrl()).isEqualTo("https://dapi.kakao.com");
        assertThat(properties.baseUrlSource()).isEqualTo("default");
    }

    @Test
    void failsFastWhenNoApiKeyIsConfigured() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.kakao.base-url", "https://dapi.kakao.com");

        assertThatThrownBy(() -> KakaoClientProperties.resolve(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Kakao api-key must be configured");
    }
}
