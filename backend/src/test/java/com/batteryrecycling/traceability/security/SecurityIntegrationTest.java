package com.batteryrecycling.traceability.security;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.batteryrecycling.traceability.common.api.IdGenerator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    IdGenerator idGenerator;

    @Test
    @Order(1)
    void loginSuccessReturnsJwtAndCurrentUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"admin","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.currentUser.username").value("admin"))
                .andExpect(jsonPath("$.data.currentUser.permissions", hasItem("permission:manage")));
    }

    @Test
    @Order(2)
    void wrongPasswordAndDisabledUserAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"admin","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"disabled_user","password":"password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void protectedEndpointsRequireValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/current-user"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @Order(4)
    void currentUserReturnsTenantRolesAndPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseId").value(1))
                .andExpect(jsonPath("$.data.roles", hasItem("SYSTEM_ADMIN")))
                .andExpect(jsonPath("$.data.permissions", hasItem("permission:manage")));
    }

    @Test
    @Order(5)
    void permissionAndTenantBoundariesAreEnforced() throws Exception {
        int deniedBefore = forbiddenAuditCount("GET /api/v1/users");
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearer(supervisorToken())))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(forbiddenAuditCount("GET /api/v1/users")).isGreaterThan(deniedBefore);

        int crossBefore = forbiddenAuditCount("USER_LIST_CROSS_ENTERPRISE_DENIED");
        mockMvc.perform(get("/api/v1/users?enterpriseId=2")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
        org.assertj.core.api.Assertions.assertThat(forbiddenAuditCount("USER_LIST_CROSS_ENTERPRISE_DENIED")).isGreaterThan(crossBefore);
    }

    @Test
    @Order(6)
    void systemAdminHasOnlyConfirmedGovernanceReadPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", hasItem("dq:rule:read")))
                .andExpect(jsonPath("$.data.permissions", hasItem("dq:audit:read")))
                .andExpect(jsonPath("$.data.permissions", not(hasItem("dq:check:execute"))))
                .andExpect(jsonPath("$.data.permissions", not(hasItem("dq:rule:toggle"))));

        Integer toggleAssignments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_permission rp
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE p.permission_code = 'dq:rule:toggle'
                """, Integer.class);
        org.assertj.core.api.Assertions.assertThat(toggleAssignments).isZero();
    }

    @Test
    @Order(7)
    void forbiddenTogglePermissionAssignmentIsRejected() throws Exception {
        Long supervisorRoleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'", Long.class);
        mockMvc.perform(put("/api/v1/roles/{id}/permissions", supervisorRoleId)
                        .header("Authorization", bearer(adminToken()))
                        .header("Idempotency-Key", "toggle-denied-key")
                        .contentType("application/json")
                        .content("""
                                {"permissionCodes":["dq:rule:read","dq:rule:toggle"]}
                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void failedWriteKeepsBusinessStateAndMarksIdempotencyFailed() throws Exception {
        String token = adminToken();
        Set<String> beforeRoles = userRoles(1L);
        mockMvc.perform(put("/api/v1/users/1/roles")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "failed-mixed-role-key")
                        .contentType("application/json")
                        .content("""
                                {"roleCodes":["SYSTEM_ADMIN","RECYCLE_OPERATOR"]}
                                """))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(userRoles(1L)).isEqualTo(beforeRoles);
        org.assertj.core.api.Assertions.assertThat(idempotencyStatus("UPDATE_USER_ROLES", "failed-mixed-role-key")).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(forbiddenAuditCount("USER_ROLE_UPDATE_DENIED")).isGreaterThan(0);
    }

    @Test
    @Order(9)
    void rbacPrivilegeEscalationBoundariesAreEnforced() throws Exception {
        String token = adminToken();
        Long systemAdminRoleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN'", Long.class);

        mockMvc.perform(put("/api/v1/roles/{id}/permissions", systemAdminRoleId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "admin-business-perm-key")
                        .contentType("application/json")
                        .content("""
                                {"permissionCodes":["authenticated","permission:manage","audit:read","batch:create"]}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/roles/{id}/permissions", systemAdminRoleId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "remove-last-admin-key")
                        .contentType("application/json")
                        .content("""
                                {"permissionCodes":["authenticated","audit:read"]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void requestValidationRejectsEmptyPayloadAndInvalidIdempotencyKey() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/api/v1/users/5/roles")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "empty-role-key")
                        .contentType("application/json")
                        .content("""
                                {"roleCodes":[]}
                                """))
                .andExpect(status().isBadRequest());

        Long supervisorRoleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'", Long.class);
        mockMvc.perform(put("/api/v1/roles/{id}/permissions", supervisorRoleId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "short")
                        .contentType("application/json")
                        .content("""
                                {"permissionCodes":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(11)
    void issuedTokenIsRejectedAfterUserDisabled() throws Exception {
        String token = tokenFor("enterprise_b_user", "password");
        jdbcTemplate.update("UPDATE sys_user SET enabled_status = 'DISABLED' WHERE id = 6");
        mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(12)
    void repeatedIdempotencyKeyReturnsFirstResultAndDifferentRequestIsConflict() throws Exception {
        String token = adminToken();
        String body = """
                {"roleCodes":["RECYCLE_OPERATOR"]}
                """;
        String first = mockMvc.perform(put("/api/v1/users/5/roles")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "same-user-role-key")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("RECYCLE_OPERATOR")))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(put("/api/v1/users/5/roles")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "same-user-role-key")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(second)).isEqualTo(objectMapper.readTree(first));

        mockMvc.perform(put("/api/v1/users/5/roles")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "same-user-role-key")
                        .contentType("application/json")
                        .content("""
                                {"roleCodes":["WAREHOUSE_ADMIN"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @Order(13)
    void rerunningV3DoesNotDuplicateOrOverwriteExistingRolePermissions() throws Exception {
        jdbcTemplate.update("""
                DELETE rp FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE r.role_code = 'SYSTEM_ADMIN' AND p.permission_code = 'audit:read'
                """);

        ScriptUtils.executeSqlScript(
                jdbcTemplate.getDataSource().getConnection(),
                new ClassPathResource("db/migration/V3__initialize_core_roles_and_permissions.sql")
        );

        Integer auditReadAssignments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE r.role_code = 'SYSTEM_ADMIN' AND p.permission_code = 'audit:read'
                """, Integer.class);
        Integer toggleAssignments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_permission rp
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE p.permission_code = 'dq:rule:toggle'
                """, Integer.class);
        org.assertj.core.api.Assertions.assertThat(auditReadAssignments).isOne();
        org.assertj.core.api.Assertions.assertThat(toggleAssignments).isZero();
    }

    @Test
    @Order(14)
    void idGeneratorProducesUniqueIdsConcurrently() throws Exception {
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        var executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 2000; i++) {
            executor.submit(() -> ids.add(idGenerator.nextId()));
        }
        executor.shutdown();
        org.assertj.core.api.Assertions.assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        org.assertj.core.api.Assertions.assertThat(ids).hasSize(2000);
    }

    private String adminToken() throws Exception {
        return tokenFor("admin", "password");
    }

    private String supervisorToken() throws Exception {
        return tokenFor("supervisor", "password");
    }

    private String tokenFor(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("data").get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String expiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(System.getenv("JWT_SECRET").getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .id("expired-test-token")
                .subject("1")
                .claim("enterpriseId", 1)
                .issuedAt(Date.from(now.minusSeconds(7200)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(key)
                .compact();
    }

    private int forbiddenAuditCount(String actionCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND result = 'FORBIDDEN'",
                Integer.class,
                actionCode);
        return count == null ? 0 : count;
    }

    private Set<String> userRoles(Long userId) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT r.role_code
                FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ?
                """, String.class, userId));
    }

    private String idempotencyStatus(String operationCode, String idempotencyKey) {
        return jdbcTemplate.queryForObject("""
                SELECT process_status
                FROM idempotency_record
                WHERE operation_code = ? AND idempotency_key = ?
                """, String.class, operationCode, idempotencyKey);
    }
}
