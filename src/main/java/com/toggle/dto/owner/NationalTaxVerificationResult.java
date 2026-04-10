package com.toggle.dto.owner;

public record NationalTaxVerificationResult(
    boolean valid,
    String requestPayloadJson,
    String responsePayloadJson,
    String matchedBusinessNumber,
    String matchedRepresentativeName,
    String matchedOpenDate,
    String matchedAddress,
    String failureCode,
    String failureMessage
) {
}
