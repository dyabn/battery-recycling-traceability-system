package com.batteryrecycling.traceability.common.api;

import java.util.UUID;

public record ApiResponse<T>(
        String code,
        String message,
        String traceId,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", UUID.randomUUID().toString(), data);
    }
}
