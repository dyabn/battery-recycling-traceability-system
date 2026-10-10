package com.batteryrecycling.traceability.inventory;

import java.time.LocalDateTime;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record InventoryItemDto(
            Long id,
            Long enterpriseId,
            Long batteryId,
            String systemTraceCode,
            Long currentResponsibleEnterpriseId,
            String currentResponsibleEnterpriseName,
            String lifecycleStatus,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            Long locationId,
            String locationCode,
            Long inboundRecordId,
            LocalDateTime inboundAt
    ) {
    }
}
