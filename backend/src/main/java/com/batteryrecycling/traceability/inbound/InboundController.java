package com.batteryrecycling.traceability.inbound;

import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import com.batteryrecycling.traceability.inbound.InboundDtos.InboundCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InboundController {
    private final InboundService inboundService;
    private final CurrentUserService currentUserService;

    public InboundController(InboundService inboundService, CurrentUserService currentUserService) {
        this.inboundService = inboundService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/inbounds/pending")
    @PreAuthorize("hasAuthority('inbound:create')")
    public ApiResponse<?> pending() {
        return ApiResponse.ok(inboundService.listPending(currentUserService.requireCurrentUser()));
    }

    @PostMapping("/api/v1/batteries/{id}/inbounds")
    @PreAuthorize("hasAuthority('inbound:create')")
    public ApiResponse<?> createInbound(@PathVariable Long id, @Valid @RequestBody InboundCreateRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(inboundService.createInbound(currentUserService.requireCurrentUser(), id, request, idempotencyKey, servletRequest));
    }

    @DeleteMapping("/api/v1/inbound-records/{id}")
    public ApiResponse<?> deleteProtected(@PathVariable Long id, HttpServletRequest servletRequest) {
        inboundService.rejectDelete(currentUserService.requireCurrentUser(), id, servletRequest);
        return ApiResponse.ok(null);
    }
}
