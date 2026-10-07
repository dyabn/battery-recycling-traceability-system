package com.batteryrecycling.traceability.acceptance;

import jakarta.validation.constraints.Size;
import java.util.List;

public final class AcceptanceDtos {
    private AcceptanceDtos() {
    }

    public record AcceptanceCreateRequest(
            String acceptanceResult,
            @Size(max = 40)
            String identityCheckResult,
            @Size(max = 40)
            String appearanceCheckResult,
            @Size(max = 40)
            String documentCheckResult,
            @Size(max = 500)
            String acceptanceNote
    ) {
    }

    public record AcceptanceSupplementRequest(
            @Size(max = 500)
            String supplementNote,
            List<Long> attachmentIds
    ) {
    }

    public record AcceptanceResult(
            Long acceptanceRecordId,
            String batteryStatus
    ) {
    }
}
