package com.batteryrecycling.traceability.user;

import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.idempotency.IdempotencyService;
import com.batteryrecycling.traceability.role.RoleDto;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;

    public UserService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, AuditService auditService, IdempotencyService idempotencyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
    }

    public CurrentUser loadCurrentUser(Long userId, Long enterpriseIdFromToken) {
        UserAccount account = findAccountById(userId);
        if (!"ENABLED".equals(account.enabledStatus())) {
            throw ApiException.unauthenticated("UNAUTHENTICATED", "用户已被禁用");
        }
        if (!account.enterpriseId().equals(enterpriseIdFromToken)) {
            throw ApiException.unauthenticated("UNAUTHENTICATED", "登录上下文无效");
        }
        return new CurrentUser(account.id(), account.enterpriseId(), account.username(), account.displayName(), roles(account.id()), permissions(account.id()));
    }

    public UserAccount findAccountByUsername(String username) {
        List<UserAccount> users = jdbcTemplate.query("""
                SELECT id, enterprise_id, username, password_hash, display_name, enabled_status
                FROM sys_user
                WHERE username = ?
                """, this::mapAccount, username);
        if (users.isEmpty()) {
            throw ApiException.unauthenticated("UNAUTHENTICATED", "用户名或密码错误");
        }
        return users.get(0);
    }

    public UserAccount findAccountById(Long id) {
        List<UserAccount> users = jdbcTemplate.query("""
                SELECT id, enterprise_id, username, password_hash, display_name, enabled_status
                FROM sys_user
                WHERE id = ?
                """, this::mapAccount, id);
        if (users.isEmpty()) {
            throw ApiException.unauthenticated("UNAUTHENTICATED", "用户不存在或已失效");
        }
        return users.get(0);
    }

    public UserDto toDto(Long userId) {
        UserAccount account = findAccountById(userId);
        return new UserDto(account.id(), account.enterpriseId(), account.username(), account.displayName(), account.enabledStatus(), new ArrayList<>(roles(userId)), new ArrayList<>(permissions(userId)));
    }

    public List<UserDto> listUsers(CurrentUser currentUser, Long requestedEnterpriseId, HttpServletRequest request) {
        if (requestedEnterpriseId != null && !requestedEnterpriseId.equals(currentUser.enterpriseId())) {
            auditService.record(currentUser.enterpriseId(), currentUser.id(), "USER_LIST_CROSS_ENTERPRISE_DENIED", "SYS_USER", null, "FORBIDDEN", "拒绝跨企业用户列表访问", request);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的数据");
        }
        return jdbcTemplate.query("""
                SELECT id
                FROM sys_user
                WHERE enterprise_id = ?
                ORDER BY id
                """, (rs, rowNum) -> toDto(rs.getLong("id")), currentUser.enterpriseId());
    }

    public UserDto updateUserRoles(CurrentUser currentUser, Long userId, Set<String> roleCodes, String idempotencyKey, HttpServletRequest request) {
        String requestHash = "user=" + userId + ";roles=" + new java.util.TreeSet<>(roleCodes);
        return idempotencyService.execute("UPDATE_USER_ROLES", idempotencyKey, requestHash, UserDto.class, () -> updateUserRolesInTransaction(currentUser, userId, roleCodes, request));
    }

    public UserDto updateUserRolesInTransaction(CurrentUser currentUser, Long userId, Set<String> roleCodes, HttpServletRequest request) {
        UserAccount target = findAccountById(userId);
        if (!target.enterpriseId().equals(currentUser.enterpriseId())) {
            auditService.record(currentUser.enterpriseId(), currentUser.id(), "USER_ROLE_CROSS_ENTERPRISE_DENIED", "SYS_USER", userId, "FORBIDDEN", "拒绝跨企业修改用户角色", request);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能修改其他企业的用户");
        }
        validateRoleBoundary(currentUser, target, roleCodes, request);
        Set<String> before = roles(userId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        for (String roleCode : roleCodes) {
            Long roleId = roleId(roleCode);
            jdbcTemplate.update(
                    "INSERT INTO sys_user_role (id, user_id, role_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP(3))",
                    idGenerator.nextId(), userId, roleId
            );
        }
        UserDto updated = toDto(userId);
        auditService.record(currentUser.enterpriseId(), currentUser.id(), "USER_ROLE_UPDATE", "SYS_USER", userId, "SUCCESS", "before=" + before + "; after=" + updated.roles(), request);
        return updated;
    }

    private void validateRoleBoundary(CurrentUser currentUser, UserAccount target, Set<String> roleCodes, HttpServletRequest request) {
        if (roleCodes.contains("SYSTEM_ADMIN") && roleCodes.size() > 1) {
            auditService.record(currentUser.enterpriseId(), currentUser.id(), "USER_ROLE_UPDATE_DENIED", "SYS_USER", target.id(), "FORBIDDEN", "系统管理员角色不得与业务角色混用", request);
            throw ApiException.forbidden("FORBIDDEN", "系统管理员角色不得与业务角色混用");
        }
        Set<String> before = roles(target.id());
        if (before.contains("SYSTEM_ADMIN") && !roleCodes.contains("SYSTEM_ADMIN") && enabledPermissionAdminCount() <= 1) {
            auditService.record(currentUser.enterpriseId(), currentUser.id(), "LAST_PERMISSION_ADMIN_REMOVAL_DENIED", "SYS_USER", target.id(), "FORBIDDEN", "不能移除最后一个有效权限管理员", request);
            throw ApiException.forbidden("FORBIDDEN", "不能移除最后一个有效权限管理员");
        }
    }

    private int enabledPermissionAdminCount() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT u.id)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role_permission rp ON rp.role_id = ur.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE u.enabled_status = 'ENABLED'
                  AND p.permission_code = 'permission:manage'
                """, Integer.class);
        return count == null ? 0 : count;
    }

    public Set<String> roles(Long userId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                SELECT r.role_code
                FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ?
                ORDER BY r.role_code
                """, String.class, userId));
    }

    public Set<String> permissions(Long userId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                SELECT DISTINCT p.permission_code
                FROM sys_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id
                JOIN sys_user_role ur ON ur.role_id = rp.role_id
                WHERE ur.user_id = ?
                ORDER BY p.permission_code
                """, String.class, userId));
    }

    private Long roleId(String roleCode) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM sys_role WHERE role_code = ?", Long.class, roleCode);
        if (ids.isEmpty()) {
            throw new ApiException("VALIDATION_FAILED", "角色不存在: " + roleCode, HttpStatus.BAD_REQUEST);
        }
        return ids.get(0);
    }

    private UserAccount mapAccount(ResultSet rs, int rowNum) throws SQLException {
        return new UserAccount(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("enabled_status")
        );
    }

    public record UserAccount(Long id, Long enterpriseId, String username, String passwordHash, String displayName, String enabledStatus) {
    }
}
