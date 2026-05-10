package com.toggle.global.exception;

import org.springframework.http.HttpStatus;

public class KakaoAddressSearchException extends RuntimeException {

    private final String path;
    private final String query;
    private final HttpStatus status;
    private final String responseBody;
    private final String reasonCode;

    public KakaoAddressSearchException(String path, String query, HttpStatus status, String responseBody, String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        this.path = path;
        this.query = query;
        this.status = status;
        this.responseBody = responseBody;
        this.reasonCode = reasonCode;
    }

    public String getPath() {
        return path;
    }

    public String getQuery() {
        return query;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
