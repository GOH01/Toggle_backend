package com.toggle.dto.kakao;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public record KakaoMapVerificationResult(
    Outcome outcome,
    String addressQuery,
    String addressApiPath,
    KakaoAddressSearchResponse addressResponse,
    AddressMatch addressMatch,
    HttpStatus failureStatus,
    String keywordQuery,
    String keywordApiPath,
    Double keywordLatitude,
    Double keywordLongitude,
    Integer keywordRadiusMeters,
    KakaoPlaceSearchResponse keywordResponse,
    int candidateCount,
    KeywordMatch selectedMatch,
    String failureCode,
    String failureMessage,
    String failureResponseBody
) {

    public enum Outcome {
        SUCCESS,
        ADDRESS_FAILURE,
        MANUAL_REVIEW_REQUIRED
    }

    public record AddressMatch(
        String externalPlaceId,
        String placeName,
        String roadAddress,
        String jibunAddress,
        String categoryName,
        BigDecimal latitude,
        BigDecimal longitude
    ) {
    }

    public record KeywordMatch(
        String externalPlaceId,
        String placeName,
        String roadAddress,
        String jibunAddress,
        String phone,
        String categoryName,
        BigDecimal latitude,
        BigDecimal longitude,
        double similarity,
        double distanceMeters
    ) {
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public boolean isAddressFailure() {
        return outcome == Outcome.ADDRESS_FAILURE;
    }

    public boolean isManualReviewRequired() {
        return outcome == Outcome.MANUAL_REVIEW_REQUIRED;
    }
}
