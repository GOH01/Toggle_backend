package com.toggle.global.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class KakaoClientConfig {

    @Bean
    @Primary
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    @Qualifier("kakaoRestTemplate")
    RestTemplate kakaoRestTemplate(
        RestTemplateBuilder builder,
        @Value("${app.kakao.api-key}") String apiKey,
        @Value("${app.kakao.base-url}") String baseUrl
    ) {
        return builder
            .rootUri(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
            .build();
    }
}
