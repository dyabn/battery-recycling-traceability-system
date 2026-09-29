package com.batteryrecycling.traceability.batch;

import com.batteryrecycling.traceability.batch.RecycleBatchDtos.AddBatteryRequest;
import com.batteryrecycling.traceability.batch.RecycleBatchDtos.RecycleBatchCreateRequest;
import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecycleBatchController {
    private final RecycleBatchService recycleBatchService;
    private final CurrentUserService currentUserService;

    public RecycleBatchController(RecycleBatchService recycleBatchService, CurrentUserService currentUserService) {
        this.recycleBatchService = recycleBatchService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/v1/recycle-batches")
    @PreAuthorize("hasAuthority('batch:create')")
    public ApiResponse<?> create(@Valid @RequestBody RecycleBatchCreateRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(recycleBatchService.create(currentUserService.requireCurrentUser(), request, idempotencyKey, servletRequest));
    }

    @GetMapping("/api/v1/recycle-batches")
    @PreAuthorize("hasAuthority('batch:read')")
    public ApiResponse<?> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(recycleBatchService.list(currentUserService.requireCurrentUser(), status));
    }

    @GetMapping("/api/v1/recycle-batches/{id}")
    @PreAuthorize("hasAuthority('batch:read')")
    public ApiResponse<?> detail(@PathVariable Long id, HttpServletRequest servletRequest) {
        return ApiResponse.ok(recycleBatchService.detail(currentUserService.requireCurrentUser(), id, servletRequest));
    }

    @PutMapping("/api/v1/recycle-batches/{id}")
    @PreAuthorize("hasAuthority('batch:create')")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody RecycleBatchCreateRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(recycleBatchService.update(currentUserService.requireCurrentUser(), id, request, idempotencyKey, servletRequest));
    }

    @PostMapping("/api/v1/recycle-batches/{id}/batteries")
    @PreAuthorize("hasAuthority('battery:create')")
    public ApiResponse<?> addBattery(@PathVariable Long id, @Valid @RequestBody AddBatteryRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(recycleBatchService.addBattery(currentUserService.requireCurrentUser(), id, request, idempotencyKey, servletRequest));
    }

    @PostMapping("/api/v1/recycle-batches/{id}/submit")
    @PreAuthorize("hasAuthority('batch:submit')")
    public ApiResponse<?> submit(@PathVariable Long id, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(recycleBatchService.submit(currentUserService.requireCurrentUser(), id, idempotencyKey, servletRequest));
    }
}
