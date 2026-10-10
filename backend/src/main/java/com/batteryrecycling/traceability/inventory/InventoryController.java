package com.batteryrecycling.traceability.inventory;

import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {
    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;

    public InventoryController(InventoryService inventoryService, CurrentUserService currentUserService) {
        this.inventoryService = inventoryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/inventory")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<?> inventory(@RequestParam(required = false) String systemTraceCode) {
        return ApiResponse.ok(inventoryService.listCurrent(currentUserService.requireCurrentUser(), systemTraceCode));
    }
}
