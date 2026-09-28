package com.batteryrecycling.traceability.role;

import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.idempotency.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RoleService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;

    public RoleService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, AuditService auditService, IdempotencyService idempotencyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
    }

    public List<RoleDto> listRoles() {
        return jdbcTemplate.query("""
                SELECT id, role_code, role_name
                FROM sys_role
                ORDER BY role_code
                """,
                (rs, rowNum) -> new RoleDto(
                        rs.getLong("id"),
                        rs.getString("role_code"),
                        rs.getString("role_name"),
                        permissionsByRoleId(rs.getLong("id"))
                ));
    }

    public RoleDto updateRolePermissions(CurrentUser currentUser, Long roleId, Set<String> permissionCodes, String idempotencyKey, HttpServletRequest request) {
        String requestHash = "role=" + roleId + ";permissions=" + new java.util.TreeSet<>(permissionCodes);
        return idempotencyService.execute("UPDATE_ROLE_PERMISSIONS", idempotencyKey, requestHash, RoleDto.class,
                () -> updateRolePermissionsInTransaction(currentUser, roleId, permissionCodes, request));
    }

    public RoleDto updateRolePermissionsInTransaction(CurrentUser currentUser, Long roleId, Set<String> permissionCodes, HttpServletRequest request) {
        RoleDto before = roleById(roleId);
        if (permissionCodes.contains("dq:rule:toggle")) {
            auditService.record(currentUser.enterpriseId(), currentUser.id(), "ROLE_PERMISSION_UPDATE_DENIED", "SYS_ROLE", roleId, "FORBIDDEN", "V1.1 不分配 dq:rule:toggle", request);
            throw ApiException.forbidden("FORBIDDEN", "V1.1 不允许分配 dq:rule:toggle 权限");
        }
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        for (String permissionCode : permissionCodes) {
            Long permissionId = permissionId(permissionCode);
            jdbcTemplate.update(
                    "INSERT INTO sys_role_permission (id, role_id, permission_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP(3))",
                    idGenerator.nextId(), roleId, permissionId
            );
        }
        RoleDto updated = roleById(roleId);
        auditService.record(currentUser.enterpriseId(), currentUser.id(), "ROLE_PERMISSION_UPDATE", "SYS_ROLE", roleId, "SUCCESS", "before=" + before.permissions() + "; after=" + updated.permissions(), request);
        return updated;
    }

    public RoleDto roleById(Long roleId) {
        List<RoleDto> roles = jdbcTemplate.query("""
                SELECT id, role_code, role_name
                FROM sys_role
                WHERE id = ?
                """,
                (rs, rowNum) -> new RoleDto(
                        rs.getLong("id"),
                        rs.getString("role_code"),
                        rs.getString("role_name"),
                        permissionsByRoleId(rs.getLong("id"))
                ),
                roleId);
        if (roles.isEmpty()) {
            throw ApiException.notFound("角色不存在");
        }
        return roles.get(0);
    }

    private Set<String> permissionsByRoleId(Long roleId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                SELECT p.permission_code
                FROM sys_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id
                WHERE rp.role_id = ?
                ORDER BY p.permission_code
                """, String.class, roleId));
    }

    private Long permissionId(String permissionCode) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM sys_permission WHERE permission_code = ?", Long.class, permissionCode);
        if (ids.isEmpty()) {
            throw new ApiException("VALIDATION_FAILED", "权限不存在: " + permissionCode, HttpStatus.BAD_REQUEST);
        }
        return ids.get(0);
    }
}

