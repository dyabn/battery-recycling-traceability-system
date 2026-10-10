package com.batteryrecycling.traceability.warehouse;

public final class WarehouseDtos {
    private WarehouseDtos() {
    }

    public record WarehouseDto(
            Long id,
            Long enterpriseId,
            String warehouseCode,
            String warehouseName,
            String enabledStatus
    ) {
    }

    public record WarehouseLocationDto(
            Long id,
            Long enterpriseId,
            Long warehouseId,
            String locationCode,
            String enabledStatus
    ) {
    }
}
