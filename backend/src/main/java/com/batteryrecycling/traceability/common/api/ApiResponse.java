package com.batteryrecycling.traceability.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public record ApiResponse<T>(
        String code,
        String message,
        String traceId,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", currentTraceId(), data);
    }

    private static String currentTraceId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Object existing = request.getAttribute("traceId");
            if (existing instanceof String traceId && !traceId.isBlank()) {
                return traceId;
            }
            String header = request.getHeader("X-Trace-Id");
            if (header != null && !header.isBlank()) {
                request.setAttribute("traceId", header);
                return header;
            }
            String generated = UUID.randomUUID().toString();
            request.setAttribute("traceId", generated);
            return generated;
        }
        return UUID.randomUUID().toString();
    }
}
