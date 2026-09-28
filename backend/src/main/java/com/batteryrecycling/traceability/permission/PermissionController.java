package com.batteryrecycling.traceability.permission;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PermissionController {
    private final JdbcTemplate jdbcTemplate;

    public PermissionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/v1/permissions")
    @PreAuthorize("hasAuthority('permission:manage')")
    public List<PermissionDto> listPermissions() {
        return jdbcTemplate.query("""
                SELECT id, permission_code, permission_name
                FROM sys_permission
                ORDER BY permission_code
                """,
                (rs, rowNum) -> new PermissionDto(
                        rs.getLong("id"),
                        rs.getString("permission_code"),
                        rs.getString("permission_name")
                ));
    }
}
