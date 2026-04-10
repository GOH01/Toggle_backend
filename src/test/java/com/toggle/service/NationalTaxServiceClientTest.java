package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.owner.NationalTaxVerificationResult;
import com.toggle.global.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

class NationalTaxServiceClientTest {

    @Test
    void shouldParseSuccessfulValidationResponse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class), anyString()))
            .thenReturn(ResponseEntity.ok("""
                {
                  "data": [
                    {
                      "valid": "01",
                      "status": {
                        "b_no": "1234567890",
                        "start_dt": "20210315"
                      }
                    }
                  ]
                }
                """));

        NationalTaxServiceClient client = new NationalTaxServiceClient(
            restTemplate,
            new ObjectMapper(),
            "test-key",
            "https://api.odcloud.kr/api/nts-businessman/v1"
        );

        NationalTaxVerificationResult result = client.verifyBusiness("1234567890", "홍길동", "20210315");

        assertThat(result.valid()).isTrue();
        assertThat(result.failureCode()).isNull();
        assertThat(result.matchedBusinessNumber()).isEqualTo("1234567890");
    }

    @Test
    void shouldParseValidationFailureMessage() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class), anyString()))
            .thenReturn(ResponseEntity.ok("""
                {
                  "data": [
                    {
                      "valid": "02",
                      "valid_msg": "대표자명과 개업일자가 일치하지 않습니다."
                    }
                  ]
                }
                """));

        NationalTaxServiceClient client = new NationalTaxServiceClient(
            restTemplate,
            new ObjectMapper(),
            "test-key",
            "https://api.odcloud.kr/api/nts-businessman/v1"
        );

        NationalTaxVerificationResult result = client.verifyBusiness("1234567890", "홍길동", "20210315");

        assertThat(result.valid()).isFalse();
        assertThat(result.failureCode()).isEqualTo("NTS_VERIFICATION_FAILED");
        assertThat(result.failureMessage()).isEqualTo("대표자명과 개업일자가 일치하지 않습니다.");
    }

    @Test
    void shouldMapNationalTaxErrorResponseToApiException() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class), anyString()))
            .thenThrow(new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                """
                {"status_code":"REQUEST_DATA_MALFORMED"}
                """.getBytes(),
                java.nio.charset.StandardCharsets.UTF_8
            ));

        NationalTaxServiceClient client = new NationalTaxServiceClient(
            restTemplate,
            new ObjectMapper(),
            "test-key",
            "https://api.odcloud.kr/api/nts-businessman/v1"
        );

        assertThatThrownBy(() -> client.verifyBusiness("1234567890", "홍길동", "20210315"))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getCode()).isEqualTo("NATIONAL_TAX_REQUEST_DATA_MALFORMED");
                assertThat(ex.getMessage()).isEqualTo("국세청 요청 필수값이 누락되었거나 형식이 올바르지 않습니다.");
            });
    }
}
