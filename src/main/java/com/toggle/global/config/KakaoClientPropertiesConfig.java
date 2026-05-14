package com.toggle.global.config;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class KakaoClientPropertiesConfig {

    private static final Logger log = LoggerFactory.getLogger(KakaoClientPropertiesConfig.class);

    @Bean
    KakaoClientProperties kakaoClientProperties(Environment environment) {
        KakaoClientProperties properties = KakaoClientProperties.resolve(environment);
        log.info(
            "Kakao config resolved activeProfiles = {}, apiKeySource = {}, baseUrlSource = {}, baseUrl = {}, apiKeyPresent = {}",
            Arrays.toString(environment.getActiveProfiles()),
            properties.apiKeySource(),
            properties.baseUrlSource(),
            properties.baseUrl(),
            properties.hasApiKey()
        );
        return properties;
    }
}
