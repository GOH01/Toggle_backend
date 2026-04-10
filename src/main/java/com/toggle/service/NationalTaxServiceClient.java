package com.toggle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.owner.NationalTaxVerificationResult;
import com.toggle.global.exception.ApiException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NationalTaxServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String serviceKey;
    private final String baseUrl;

    public NationalTaxServiceClient(
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        @Value("${app.national-tax.service-key:}") String serviceKey,
        @Value("${app.national-tax.base-url:https://api.odcloud.kr/api/nts-businessman/v1}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
    }

    public NationalTaxVerificationResult verifyBusiness(
        String businessNumber,
        String representativeName,
        String businessOpenDate,
        String businessAddress
    ) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "NATIONAL_TAX_NOT_CONFIGURED", "국세청 API 설정이 필요합니다.");
        }

        Map<String, Object> body = Map.of(
            "businesses",
            java.util.List.of(Map.of(
                "b_no", businessNumber,
                "start_dt", businessOpenDate,
                "p_nm", representativeName,
                "b_adr", businessAddress
            ))
        );

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/validate?serviceKey={serviceKey}",
                HttpMethod.POST,
                new HttpEntity<>(requestJson, headers),
                String.class,
                serviceKey
            );

            String responseBody = response.getBody() == null ? "" : response.getBody();
            boolean valid = responseBody.contains("\"valid\":\"01\"") || responseBody.contains("\"valid\": \"01\"");

            return new NationalTaxVerificationResult(
                valid,
                requestJson,
                responseBody,
                businessNumber,
                representativeName,
                businessOpenDate,
                businessAddress,
                valid ? null : "NTS_VERIFICATION_FAILED",
                valid ? null : "국세청 진위확인 결과가 일치하지 않습니다."
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "NATIONAL_TAX_API_ERROR", "국세청 API 호출에 실패했습니다.");
        }
    }
}
