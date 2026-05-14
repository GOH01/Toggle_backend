package com.toggle.global.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public record KakaoClientProperties(
    String apiKey,
    String baseUrl,
    String apiKeySource,
    String baseUrlSource
) {

    private static final String DEFAULT_BASE_URL = "https://dapi.kakao.com";

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }

    public boolean hasBaseUrl() {
        return StringUtils.hasText(baseUrl);
    }

    public String maskedApiKey() {
        if (!hasApiKey()) {
            return "<missing>";
        }

        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }

        return trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    public static KakaoClientProperties resolve(Environment environment) {
        ResolvedValue apiKey = firstNonBlank(
            environment,
            "app.kakao.api-key",
            "kakao.api-key"
        );
        ResolvedValue baseUrl = firstNonBlank(
            environment,
            "app.kakao.base-url",
            "kakao.base-url"
        );

        String resolvedApiKey = apiKey.value();
        String resolvedBaseUrl = StringUtils.hasText(baseUrl.value()) ? baseUrl.value() : DEFAULT_BASE_URL;
        String resolvedBaseUrlSource = StringUtils.hasText(baseUrl.value()) ? baseUrl.source() : "default";

        if (!StringUtils.hasText(resolvedApiKey)) {
            throw new IllegalStateException("Kakao api-key must be configured with app.kakao.api-key or kakao.api-key");
        }

        return new KakaoClientProperties(
            resolvedApiKey.trim(),
            resolvedBaseUrl.trim(),
            apiKey.source(),
            resolvedBaseUrlSource
        );
    }

    private static ResolvedValue firstNonBlank(Environment environment, String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (StringUtils.hasText(value)) {
                return new ResolvedValue(value.trim(), key);
            }
        }
        return new ResolvedValue(null, "missing");
    }

    private record ResolvedValue(String value, String source) {
    }
}
