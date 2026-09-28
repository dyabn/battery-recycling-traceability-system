package com.batteryrecycling.traceability.security;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.batteryrecycling.traceability.audit.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.batteryrecycling.traceability.common.api.IdGenerator;
import java.util.ArrayList;
import java.util.List;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    AuditService auditService;

    @Autowired
    TransactionTemplate transactionTemplate;

    private static final Map<String, Set<String>> EXPECTED_ROLE_PERMISSIONS = Map.of(
            "SYSTEM_ADMIN", Set.of(
                    "authenticated", "permission:manage", "audit:read", "dq:rule:read",
                    "dq:issue:read", "dq:dashboard:read", "dq:audit:read"
            ),
            "BUSINESS_SUPERVISOR", Set.of(
                    "authenticated", "batch:read", "inventory:read", "trace:read", "attachment:read",
                    "dq:rule:read", "dq:check:execute", "dq:check:read", "dq:issue:read",
                    "dq:issue:assign", "dq:issue:recheck", "dq:dashboard:read", "dq:audit:read"
            ),
            "RECYCLE_OPERATOR", Set.of(
                    "authenticated", "batch:create", "batch:read", "batch:submit", "battery:create",
                    "battery:duplicate:resolve", "acceptance:create", "acceptance:supplement",
                    "attachment:upload", "attachment:read", "trace:read", "dq:issue:read", "dq:issue:process"
            ),
            "WAREHOUSE_ADMIN", Set.of(
                    "authenticated", "batch:read", "inbound:create", "inventory:read", "warehouse:read",
                    "attachment:upload", "attachment:read", "trace:read", "dq:issue:read", "dq:issue:process"
            )
    );

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
    void businessRoleCannotReceivePermissionManage() throws Exception {
        Long recycleRoleId = roleId("RECYCLE_OPERATOR");
        Set<String> before = rolePermissions("RECYCLE_OPERATOR");

        mockMvc.perform(put("/api/v1/roles/{id}/permissions", recycleRoleId)
                        .header("Authorization", bearer(adminToken()))
                        .header("Idempotency-Key", "business-role-privilege-key")
                        .contentType("application/json")
                        .content("""
                                {"permissionCodes":["authenticated","batch:create","permission:manage"]}
                                """))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(rolePermissions("RECYCLE_OPERATOR")).isEqualTo(before);
        org.assertj.core.api.Assertions.assertThat(idempotencyStatus("UPDATE_ROLE_PERMISSIONS", "business-role-privilege-key")).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(forbiddenAuditCount("ROLE_PERMISSION_BOUNDARY_DENIED")).isGreaterThan(0);
    }

    @Test
    @Order(9)
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
    @Order(10)
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
    @Order(11)
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
    @Order(12)
    void issuedTokenIsRejectedAfterUserDisabled() throws Exception {
        String token = tokenFor("enterprise_b_user", "password");
        jdbcTemplate.update("UPDATE sys_user SET enabled_status = 'DISABLED' WHERE id = 6");
        mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(13)
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
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(second).get("data")).isEqualTo(objectMapper.readTree(first).get("data"));

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
    @Order(14)
    void v1RolePermissionMatrixMatchesConfirmedBaseline() {
        EXPECTED_ROLE_PERMISSIONS.forEach((roleCode, expectedPermissions) ->
                org.assertj.core.api.Assertions.assertThat(rolePermissions(roleCode)).isEqualTo(expectedPermissions)
        );
    }

    @Test
    @Order(15)
    void v11GovernancePermissionMatrixMatchesConfirmedBaseline() {
        org.assertj.core.api.Assertions.assertThat(rolePermissions("SYSTEM_ADMIN"))
                .contains("dq:rule:read", "dq:issue:read", "dq:dashboard:read", "dq:audit:read")
                .doesNotContain("dq:check:execute", "dq:issue:assign", "dq:issue:process", "dq:issue:recheck", "dq:rule:toggle");
        org.assertj.core.api.Assertions.assertThat(rolePermissions("BUSINESS_SUPERVISOR"))
                .contains("dq:rule:read", "dq:check:execute", "dq:check:read", "dq:issue:read", "dq:issue:assign", "dq:issue:recheck", "dq:dashboard:read", "dq:audit:read")
                .doesNotContain("dq:issue:process", "dq:rule:toggle");
        org.assertj.core.api.Assertions.assertThat(rolePermissions("RECYCLE_OPERATOR"))
                .contains("dq:issue:read", "dq:issue:process")
                .doesNotContain("dq:check:execute", "dq:issue:assign", "dq:issue:recheck", "dq:rule:toggle");
        org.assertj.core.api.Assertions.assertThat(rolePermissions("WAREHOUSE_ADMIN"))
                .contains("dq:issue:read", "dq:issue:process")
                .doesNotContain("dq:check:execute", "dq:issue:assign", "dq:issue:recheck", "dq:rule:toggle");
    }

    @Test
    @Order(16)
    void rerunningV3AndV5KeepsCorrectFinalRolePermissionMatrix() throws Exception {
        jdbcTemplate.update("""
                DELETE rp FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE r.role_code = 'RECYCLE_OPERATOR' AND p.permission_code = 'acceptance:create'
                """);

        ScriptUtils.executeSqlScript(
                jdbcTemplate.getDataSource().getConnection(),
                new ClassPathResource("db/migration/V3__initialize_core_roles_and_permissions.sql")
        );
        ScriptUtils.executeSqlScript(
                jdbcTemplate.getDataSource().getConnection(),
                new ClassPathResource("db/migration/V5__correct_core_role_permission_matrix.sql")
        );

        Integer toggleAssignments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_permission rp
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE p.permission_code = 'dq:rule:toggle'
                """, Integer.class);
        org.assertj.core.api.Assertions.assertThat(toggleAssignments).isZero();
        EXPECTED_ROLE_PERMISSIONS.forEach((roleCode, expectedPermissions) ->
                org.assertj.core.api.Assertions.assertThat(rolePermissions(roleCode)).isEqualTo(expectedPermissions)
        );
    }

    @Test
    @Order(17)
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

    @Test
    @Order(18)
    void concurrentSameIdempotencyKeyExecutesBusinessWriteOnce() throws Exception {
        String token = adminToken();
        String key = "concurrent-role-key";
        String body = """
                {"roleCodes":["WAREHOUSE_ADMIN"]}
                """;
        int successBefore = successAuditCount("USER_ROLE_UPDATE", 4L);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(6);
        List<java.util.concurrent.Future<Integer>> responses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            responses.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(put("/api/v1/users/4/roles")
                                .header("Authorization", bearer(token))
                                .header("Idempotency-Key", key)
                                .contentType("application/json")
                                .content(body))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }
        start.countDown();
        executor.shutdown();
        org.assertj.core.api.Assertions.assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        List<Integer> statuses = new ArrayList<>();
        for (java.util.concurrent.Future<Integer> response : responses) {
            statuses.add(response.get());
        }
        org.assertj.core.api.Assertions.assertThat(statuses).contains(200);
        org.assertj.core.api.Assertions.assertThat(successAuditCount("USER_ROLE_UPDATE", 4L)).isEqualTo(successBefore + 1);
        org.assertj.core.api.Assertions.assertThat(idempotencyRecordCount(1L, 1L, "UPDATE_USER_ROLES", key)).isOne();
    }

    @Test
    @Order(19)
    void i1ApiResponsesMatchEnvelopeAndDtoContract() throws Exception {
        String token = adminToken();
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"admin","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.currentUser.enterpriseId").value(1))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(login).get("data").has("currentUser")).isTrue();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.enterpriseId").value(1))
                .andExpect(jsonPath("$.data.roles", hasItem("SYSTEM_ADMIN")))
                .andExpect(jsonPath("$.data.permissions", hasItem("permission:manage")));

        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].enterpriseId").value(1))
                .andExpect(jsonPath("$.data[0].roles").isArray())
                .andExpect(jsonPath("$.data[0].permissions").isArray());

        mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].roleCode").exists())
                .andExpect(jsonPath("$.data[0].permissions").isArray());

        mockMvc.perform(get("/api/v1/permissions").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].permissionCode").exists())
                .andExpect(jsonPath("$.data[0].permissionName").exists());

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(20)
    void successAuditRollsBackWithBusinessTransactionButRejectedAuditPersists() {
        int successBefore = successAuditCount("TX_ROLLBACK_SUCCESS_AUDIT", 999L);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                auditService.recordSuccess(1L, 1L, "TX_ROLLBACK_SUCCESS_AUDIT", "TEST", 999L, "should rollback", null);
                throw new IllegalStateException("rollback success audit");
            });
        } catch (IllegalStateException ignored) {
        }
        org.assertj.core.api.Assertions.assertThat(successAuditCount("TX_ROLLBACK_SUCCESS_AUDIT", 999L)).isEqualTo(successBefore);

        int rejectedBefore = forbiddenAuditCount("TX_ROLLBACK_REJECTED_AUDIT");
        try {
            transactionTemplate.executeWithoutResult(status -> {
                auditService.recordRejected(1L, 1L, "TX_ROLLBACK_REJECTED_AUDIT", "TEST", 998L, "FORBIDDEN", "should persist", null);
                throw new IllegalStateException("rollback rejected audit");
            });
        } catch (IllegalStateException ignored) {
        }
        org.assertj.core.api.Assertions.assertThat(forbiddenAuditCount("TX_ROLLBACK_REJECTED_AUDIT")).isEqualTo(rejectedBefore + 1);
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

    private int successAuditCount(String actionCode, Long objectId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND object_id = ? AND result = 'SUCCESS'",
                Integer.class,
                actionCode,
                objectId);
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

    private Set<String> rolePermissions(String roleCode) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT p.permission_code
                FROM sys_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id
                JOIN sys_role r ON r.id = rp.role_id
                WHERE r.role_code = ?
                """, String.class, roleCode));
    }

    private Long roleId(String roleCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class, roleCode);
    }

    private String idempotencyStatus(String operationCode, String idempotencyKey) {
        return jdbcTemplate.queryForObject("""
                SELECT process_status
                FROM idempotency_record
                WHERE operation_code = ? AND idempotency_key = ?
                """, String.class, operationCode, idempotencyKey);
    }

    private int idempotencyRecordCount(Long enterpriseId, Long operatorUserId, String operationCode, String idempotencyKey) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM idempotency_record
                WHERE enterprise_id = ?
                  AND operator_user_id = ?
                  AND operation_code = ?
                  AND idempotency_key = ?
                """, Integer.class, enterpriseId, operatorUserId, operationCode, idempotencyKey);
        return count == null ? 0 : count;
    }
}
