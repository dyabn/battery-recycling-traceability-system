package com.batteryrecycling.traceability.warehouse;

import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WarehouseController {
    private final WarehouseService warehouseService;
    private final CurrentUserService currentUserService;

    public WarehouseController(WarehouseService warehouseService, CurrentUserService currentUserService) {
        this.warehouseService = warehouseService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/warehouses")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<?> warehouses() {
        return ApiResponse.ok(warehouseService.listWarehouses(currentUserService.requireCurrentUser()));
    }

    @GetMapping("/api/v1/warehouses/{id}/locations")
    @PreAuthorize("hasAuthority('warehouse:read')")
    public ApiResponse<?> locations(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.listLocations(currentUserService.requireCurrentUser(), id, request));
    }
}
