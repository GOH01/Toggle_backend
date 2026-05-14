package com.toggle.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class KakaoClientConfig {

    private static final Logger log = LoggerFactory.getLogger(KakaoClientConfig.class);

    @Bean
    @Primary
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    @Qualifier("kakaoRestTemplate")
    RestTemplate kakaoRestTemplate(
        RestTemplateBuilder builder,
        KakaoClientProperties properties
    ) {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("Kakao api-key must be configured before creating kakaoRestTemplate");
        }
        log.info(
            "Kakao authorization header configured = {}, apiKeySource = {}, baseUrl = {}",
            properties.hasApiKey(),
            properties.apiKeySource(),
            properties.baseUrl()
        );
        return builder
            .rootUri(properties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.apiKey())
            .build();
    }
}
