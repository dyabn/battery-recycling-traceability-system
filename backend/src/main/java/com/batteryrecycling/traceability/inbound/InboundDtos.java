package com.batteryrecycling.traceability.inbound;

import jakarta.validation.constraints.NotNull;

public final class InboundDtos {
    private InboundDtos() {
    }

    public record InboundCreateRequest(
            @NotNull Long warehouseId,
            @NotNull Long locationId
    ) {
    }

    public record InboundResult(
            Long inboundRecordId,
            Long inventoryId,
            String batteryStatus
    ) {
    }
}
