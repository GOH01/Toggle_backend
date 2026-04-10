package com.toggle.dto.owner;

import java.time.LocalDateTime;

public record BusinessVerificationHistoryResponse(
    String verificationType,
    String status,
    String failureCode,
    String failureMessage,
    LocalDateTime verifiedAt
) {
}
