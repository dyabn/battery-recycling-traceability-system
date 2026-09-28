package com.batteryrecycling.traceability.audit;

import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    public AuditController(JdbcTemplate jdbcTemplate, CurrentUserService currentUserService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/audit-logs")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<List<AuditLogDto>> listAuditLogs(@RequestParam(defaultValue = "50") int size) {
        CurrentUser user = currentUserService.requireCurrentUser();
        int limit = Math.max(1, Math.min(size, 100));
        return ApiResponse.ok(jdbcTemplate.query("""
                SELECT id, enterprise_id, operator_user_id, action_code, object_type, object_id,
                       result, reject_reason, operated_at
                FROM audit_log
                WHERE enterprise_id = ?
                ORDER BY operated_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new AuditLogDto(
                        rs.getLong("id"),
                        rs.getLong("enterprise_id"),
                        rs.getObject("operator_user_id", Long.class),
                        rs.getString("action_code"),
                        rs.getString("object_type"),
                        rs.getObject("object_id", Long.class),
                        rs.getString("result"),
                        rs.getString("reject_reason"),
                        rs.getTimestamp("operated_at").toLocalDateTime()
                ),
                user.enterpriseId(),
                limit
        ));
    }
}
