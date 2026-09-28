package com.batteryrecycling.traceability.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static ApiException unauthenticated(String code, String message) {
        return new ApiException(code, message, HttpStatus.UNAUTHORIZED);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(code, message, HttpStatus.FORBIDDEN);
    }

    public static ApiException notFound(String message) {
        return new ApiException("RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(code, message, HttpStatus.CONFLICT);
    }
}

