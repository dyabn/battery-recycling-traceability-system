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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
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

    @Test
    @Order(1)
    void loginSuccessReturnsJwtAndCurrentUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"admin","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.currentUser.username").value("admin"))
                .andExpect(jsonPath("$.currentUser.permissions", hasItem("permission:manage")));
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
                .andExpect(jsonPath("$.enterpriseId").value(1))
                .andExpect(jsonPath("$.roles", hasItem("SYSTEM_ADMIN")))
                .andExpect(jsonPath("$.permissions", hasItem("permission:manage")));
    }

    @Test
    @Order(5)
    void permissionAndTenantBoundariesAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearer(supervisorToken())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users?enterpriseId=2")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
    }

    @Test
    @Order(6)
    void systemAdminHasOnlyConfirmedGovernanceReadPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("dq:rule:read")))
                .andExpect(jsonPath("$.permissions", hasItem("dq:audit:read")))
                .andExpect(jsonPath("$.permissions", not(hasItem("dq:check:execute"))))
                .andExpect(jsonPath("$.permissions", not(hasItem("dq:rule:toggle"))));

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
                .andExpect(jsonPath("$.roles", hasItem("RECYCLE_OPERATOR")))
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
    @Order(9)
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
        return json.get("accessToken").asText();
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
}
