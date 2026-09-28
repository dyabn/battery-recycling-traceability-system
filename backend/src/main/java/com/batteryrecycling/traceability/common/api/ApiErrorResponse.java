package com.batteryrecycling.traceability.common.api;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        String code,
        String message,
        String traceId,
        OffsetDateTime timestamp
) {
}

