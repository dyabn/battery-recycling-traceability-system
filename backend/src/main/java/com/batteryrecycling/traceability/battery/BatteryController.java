package com.batteryrecycling.traceability.battery;

import com.batteryrecycling.traceability.battery.BatteryDtos.BatteryCreateRequest;
import com.batteryrecycling.traceability.battery.BatteryDtos.DuplicateCheckRequest;
import com.batteryrecycling.traceability.battery.BatteryDtos.DuplicateResolutionRequest;
import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BatteryController {
    private final BatteryService batteryService;
    private final CurrentUserService currentUserService;

    public BatteryController(BatteryService batteryService, CurrentUserService currentUserService) {
        this.batteryService = batteryService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/v1/batteries")
    @PreAuthorize("hasAuthority('battery:create')")
    public ApiResponse<?> createBattery(@Valid @RequestBody BatteryCreateRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(batteryService.createBattery(currentUserService.requireCurrentUser(), request, idempotencyKey, servletRequest));
    }

    @PostMapping("/api/v1/batteries/duplicate-check")
    @PreAuthorize("hasAuthority('battery:create')")
    public ApiResponse<?> checkDuplicate(@Valid @RequestBody DuplicateCheckRequest request) {
        return ApiResponse.ok(batteryService.checkDuplicate(currentUserService.requireCurrentUser(), request));
    }

    @PostMapping("/api/v1/battery-registration-candidates/{id}/duplicate-resolution")
    @PreAuthorize("hasAuthority('battery:duplicate:resolve')")
    public ApiResponse<?> resolveDuplicate(@PathVariable Long id, @Valid @RequestBody DuplicateResolutionRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(batteryService.resolveDuplicate(currentUserService.requireCurrentUser(), id, request, idempotencyKey, servletRequest));
    }

    @GetMapping("/api/v1/batteries/{id}/trace")
    @PreAuthorize("hasAuthority('trace:read')")
    public ApiResponse<?> trace(@PathVariable Long id, HttpServletRequest servletRequest) {
        return ApiResponse.ok(batteryService.trace(currentUserService.requireCurrentUser(), id, servletRequest));
    }
}
