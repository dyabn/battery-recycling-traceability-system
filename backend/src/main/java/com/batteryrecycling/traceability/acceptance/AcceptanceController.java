package com.batteryrecycling.traceability.acceptance;

import com.batteryrecycling.traceability.acceptance.AcceptanceDtos.AcceptanceCreateRequest;
import com.batteryrecycling.traceability.acceptance.AcceptanceDtos.AcceptanceSupplementRequest;
import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
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
public class AcceptanceController {
    private final AcceptanceService acceptanceService;
    private final CurrentUserService currentUserService;

    public AcceptanceController(AcceptanceService acceptanceService, CurrentUserService currentUserService) {
        this.acceptanceService = acceptanceService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/acceptances/pending")
    @PreAuthorize("hasAuthority('acceptance:create')")
    public ApiResponse<?> pending() {
        return ApiResponse.ok(acceptanceService.listPending(currentUserService.requireCurrentUser()));
    }

    @PostMapping("/api/v1/batteries/{id}/acceptances")
    @PreAuthorize("hasAuthority('acceptance:create')")
    public ApiResponse<?> createAcceptance(@PathVariable Long id, @Valid @RequestBody AcceptanceCreateRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(acceptanceService.createAcceptance(currentUserService.requireCurrentUser(), id, request, idempotencyKey, servletRequest));
    }

    @PostMapping("/api/v1/batteries/{id}/acceptance-supplements")
    @PreAuthorize("hasAuthority('acceptance:supplement')")
    public ApiResponse<?> supplement(@PathVariable Long id, @Valid @RequestBody AcceptanceSupplementRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(acceptanceService.supplement(currentUserService.requireCurrentUser(), id, request, idempotencyKey, servletRequest));
    }

    @DeleteMapping("/api/v1/acceptance-records/{id}")
    public ApiResponse<?> deleteProtected(@PathVariable Long id, HttpServletRequest servletRequest) {
        acceptanceService.rejectDelete(currentUserService.requireCurrentUser(), id, servletRequest);
        return ApiResponse.ok(null);
    }
}
