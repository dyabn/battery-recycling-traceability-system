package com.batteryrecycling.traceability.audit;

import java.time.LocalDateTime;

public record AuditLogDto(
        Long id,
        Long enterpriseId,
        Long operatorUserId,
        String actionCode,
        String objectType,
        Long objectId,
        String result,
        String rejectReason,
        LocalDateTime operatedAt
) {
}

