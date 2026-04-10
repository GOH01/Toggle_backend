package com.toggle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.web.client.HttpStatusCodeException;
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
        String businessOpenDate
    ) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "NATIONAL_TAX_NOT_CONFIGURED", "국세청 API 설정이 필요합니다.");
        }

        Map<String, Object> body = Map.of(
            "businesses",
            java.util.List.of(Map.of(
                "b_no", businessNumber,
                "start_dt", businessOpenDate,
                "p_nm", representativeName
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
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode firstDataNode = root.path("data").isArray() && root.path("data").size() > 0
                ? root.path("data").get(0)
                : null;

            String validCode = textOrNull(firstDataNode, "valid");
            String validMessage = textOrNull(firstDataNode, "valid_msg");
            JsonNode statusNode = firstDataNode == null ? null : firstDataNode.path("status");
            boolean valid = "01".equals(validCode);
            String failureCode = valid ? null : failureCodeFrom(validCode, textOrNull(root, "status_code"));
            String failureMessage = valid
                ? null
                : enrichVerificationFailureMessage(firstNonBlank(
                    validMessage,
                    textOrNull(root, "message"),
                    "국세청 진위확인 결과가 일치하지 않습니다."
                ));

            return new NationalTaxVerificationResult(
                valid,
                requestJson,
                responseBody,
                firstNonBlank(textOrNull(statusNode, "b_no"), businessNumber),
                representativeName,
                firstNonBlank(textOrNull(statusNode, "start_dt"), businessOpenDate),
                textOrNull(statusNode, "b_adr"),
                failureCode,
                failureMessage
            );
        } catch (HttpStatusCodeException ex) {
            throw toApiException(ex);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "NATIONAL_TAX_API_ERROR", "국세청 API 호출에 실패했습니다.");
        }
    }

    private ApiException toApiException(HttpStatusCodeException ex) {
        String responseBody = ex.getResponseBodyAsString();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String statusCode = textOrNull(root, "status_code");
            String message = firstNonBlank(textOrNull(root, "message"), mapNationalTaxErrorMessage(statusCode));
            return new ApiException(
                ex.getStatusCode().is5xxServerError() ? org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE : org.springframework.http.HttpStatus.BAD_REQUEST,
                mapNationalTaxErrorCode(statusCode),
                firstNonBlank(message, "국세청 API 호출에 실패했습니다.")
            );
        } catch (Exception parseException) {
            return new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "NATIONAL_TAX_API_ERROR", "국세청 API 호출에 실패했습니다.");
        }
    }

    private String mapNationalTaxErrorCode(String statusCode) {
        if (statusCode == null || statusCode.isBlank()) {
            return "NATIONAL_TAX_API_ERROR";
        }
        return "NATIONAL_TAX_" + statusCode;
    }

    private String mapNationalTaxErrorMessage(String statusCode) {
        return switch (statusCode == null ? "" : statusCode) {
            case "BAD_JSON_REQUEST" -> "국세청 요청 JSON 형식이 올바르지 않습니다.";
            case "REQUEST_DATA_MALFORMED" -> "국세청 요청 필수값이 누락되었거나 형식이 올바르지 않습니다.";
            case "TOO_LARGE_REQUEST" -> "국세청 요청 건수가 허용 범위를 초과했습니다.";
            case "INTERNAL_ERROR" -> "국세청 서버 내부 오류가 발생했습니다.";
            case "HTTP_ERROR" -> "국세청 HTTP 통신 오류가 발생했습니다.";
            default -> null;
        };
    }

    private String failureCodeFrom(String validCode, String statusCode) {
        if ("02".equals(validCode)) {
            return "NTS_VERIFICATION_FAILED";
        }
        if (statusCode != null && !statusCode.isBlank()) {
            return mapNationalTaxErrorCode(statusCode);
        }
        return "NTS_VERIFICATION_FAILED";
    }

    private String enrichVerificationFailureMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "국세청 진위확인 결과가 일치하지 않습니다.";
        }
        if ("확인할 수 없습니다.".equals(rawMessage)) {
            return "확인할 수 없습니다. 사업자등록번호, 대표자명, 개업일자, 주소를 다시 확인해 주세요.";
        }
        return rawMessage;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
