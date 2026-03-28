package com.toggle.global.response;

public record ErrorResponse(
    String code,
    String message
) {
}
