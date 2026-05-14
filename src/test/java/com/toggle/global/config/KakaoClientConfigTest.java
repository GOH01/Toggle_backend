package com.toggle.global.config;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class KakaoClientConfigTest {

    @Test
    void kakaoRestTemplateUsesResolvedAuthorizationHeaderAndRootUri() {
        KakaoClientConfig config = new KakaoClientConfig();
        KakaoClientProperties properties = new KakaoClientProperties(
            "test-kakao-key",
            "https://dapi.kakao.com",
            "kakao.api-key",
            "kakao.base-url"
        );

        RestTemplate restTemplate = config.kakaoRestTemplate(new RestTemplateBuilder(), properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(requestTo("https://dapi.kakao.com/v2/local/search/address.json"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-kakao-key"))
            .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        restTemplate.getForObject("/v2/local/search/address.json", String.class);

        server.verify();
    }
}
