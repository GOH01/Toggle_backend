package com.toggle.dto.owner;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.AssertTrue;

public record OwnerApplicationApproveRequest(
    @AssertTrue(message = "관리자 최종 확인 여부를 체크해 주세요.")
    boolean adminConfirmed
) {
}
