package com.batteryrecycling.traceability.dataquality;

import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import com.batteryrecycling.traceability.dataquality.DataQualityDtos.CreateCheckRunRequest;
import com.batteryrecycling.traceability.dataquality.DataQualityDtos.ToggleRuleStatusRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataQualityController {
    private final DataQualityService dataQualityService;
    private final CurrentUserService currentUserService;

    public DataQualityController(DataQualityService dataQualityService, CurrentUserService currentUserService) {
        this.dataQualityService = dataQualityService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/data-quality/rules")
    @PreAuthorize("hasAuthority('dq:rule:read')")
    public ApiResponse<?> rules() {
        return ApiResponse.ok(dataQualityService.listRules(currentUserService.requireCurrentUser()));
    }

    @PatchMapping("/api/v1/data-quality/rules/{ruleCode}/status")
    @PreAuthorize("hasAuthority('dq:rule:toggle')")
    public ApiResponse<?> toggleRule(@PathVariable String ruleCode, @Valid @RequestBody ToggleRuleStatusRequest request, HttpServletRequest servletRequest) {
        dataQualityService.rejectRuleToggle(currentUserService.requireCurrentUser(), ruleCode, request, servletRequest);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/v1/data-quality/check-runs")
    @PreAuthorize("hasAuthority('dq:check:execute')")
    public ResponseEntity<ApiResponse<?>> createCheckRun(@Valid @RequestBody CreateCheckRunRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dataQualityService.createCheckRun(currentUserService.requireCurrentUser(), request, idempotencyKey, servletRequest)));
    }

    @GetMapping("/api/v1/data-quality/check-runs/{runId}")
    @PreAuthorize("hasAuthority('dq:check:read')")
    public ApiResponse<?> checkRun(@PathVariable Long runId, HttpServletRequest servletRequest) {
        return ApiResponse.ok(dataQualityService.getCheckRun(currentUserService.requireCurrentUser(), runId, servletRequest));
    }

    @GetMapping("/api/v1/data-quality/check-runs/{runId}/results")
    @PreAuthorize("hasAuthority('dq:check:read')")
    public ApiResponse<?> checkResults(@PathVariable Long runId, HttpServletRequest servletRequest) {
        return ApiResponse.ok(dataQualityService.listCheckResults(currentUserService.requireCurrentUser(), runId, servletRequest));
    }

    @GetMapping("/api/v1/data-quality/issues")
    @PreAuthorize("hasAuthority('dq:issue:read')")
    public ApiResponse<?> issues(@RequestParam(required = false) String status, HttpServletRequest servletRequest) {
        return ApiResponse.ok(dataQualityService.listIssues(currentUserService.requireCurrentUser(), status, servletRequest));
    }

    @GetMapping("/api/v1/data-quality/issues/{issueId}")
    @PreAuthorize("hasAuthority('dq:issue:read')")
    public ApiResponse<?> issue(@PathVariable Long issueId, HttpServletRequest servletRequest) {
        return ApiResponse.ok(dataQualityService.getIssue(currentUserService.requireCurrentUser(), issueId, servletRequest));
    }
}
