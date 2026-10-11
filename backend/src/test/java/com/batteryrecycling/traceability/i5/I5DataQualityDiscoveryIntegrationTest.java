package com.batteryrecycling.traceability.i5;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class I5DataQualityDiscoveryIntegrationTest {
    private static final long WAREHOUSE_A = 20101L;
    private static final long WAREHOUSE_A_DISABLED = 20102L;
    private static final long LOCATION_A = 21101L;
    private static final long LOCATION_A_MISMATCH = 21103L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    IdGenerator idGenerator;

    @Test
    void rulesAndV7BackfillAreVisibleAndDoNotOverrideDisabledConfig() throws Exception {
        String supervisorToken = tokenFor("supervisor", "password");
        mockMvc.perform(get("/api/v1/data-quality/rules").header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[*].ruleCode", hasItem("DQ-001")))
                .andExpect(jsonPath("$.data[*].id", everyItem(startsWith(""))))
                .andExpect(jsonPath("$.data[*].enabledStatus", everyItem(equalTo("ENABLED"))));

        Assertions.assertThat(configCount(1L)).isEqualTo(7);
        Assertions.assertThat(configCount(2L)).isEqualTo(7);

        jdbcTemplate.update("""
                UPDATE dq_enterprise_rule_config
                SET enabled_status = 'DISABLED', toggled_reason = 'test keep disabled'
                WHERE enterprise_id = 1 AND rule_code = 'DQ-007'
                """);
        ScriptUtils.executeSqlScript(
                jdbcTemplate.getDataSource().getConnection(),
                new ClassPathResource("db/migration/V7__backfill_data_governance_rule_configs.sql")
        );
        Assertions.assertThat(enabledStatus(1L, "DQ-007")).isEqualTo("DISABLED");
        jdbcTemplate.update("UPDATE dq_enterprise_rule_config SET enabled_status = 'ENABLED', toggled_reason = NULL WHERE enterprise_id = 1 AND rule_code = 'DQ-007'");

        int deniedBefore = dqAuditCount("DQ_ACCESS_DENIED", "FORBIDDEN");
        mockMvc.perform(patch("/api/v1/data-quality/rules/DQ-001/status")
                        .header("Authorization", bearer(supervisorToken))
                        .header("Idempotency-Key", "i5-toggle-denied")
                        .contentType("application/json")
                        .content("""
                                {"enabledStatus":"DISABLED","reason":"I5 should reject"}
                                """))
                .andExpect(status().isForbidden());
        Assertions.assertThat(dqAuditCount("DQ_ACCESS_DENIED", "FORBIDDEN")).isGreaterThan(deniedBefore);
        Assertions.assertThat(enabledStatus(1L, "DQ-001")).isEqualTo("ENABLED");
    }

    @Test
    void manualCheckCreatesResultsOpenIssuesAndDeduplicatesWithIdempotency() throws Exception {
        String supervisorToken = tokenFor("supervisor", "password");
        insertRuleViolationFixtures("MAIN");

        JsonNode firstRun = postCheckRun(supervisorToken, "i5-check-main", """
                {"runScope":"ALL"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.executedRuleCodes.length()").value(7))
                .andExpect(jsonPath("$.data.issueCount", greaterThanOrEqualTo(6)))
                .andReturnJson().get("data");
        String runId = firstRun.get("id").asText();

        mockMvc.perform(get("/api/v1/data-quality/check-runs/{runId}", runId).header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId))
                .andExpect(jsonPath("$.data.runNo").value(firstRun.get("runNo").asText()));
        mockMvc.perform(get("/api/v1/data-quality/check-runs/{runId}/results", runId).header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[*].ruleCode", hasItem("DQ-007")));
        mockMvc.perform(get("/api/v1/data-quality/issues").param("status", "OPEN").header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].ruleCode", hasItem("DQ-003")))
                .andExpect(jsonPath("$.data[*].issueStatus", everyItem(equalTo("OPEN"))));

        JsonNode sameKeyRun = postCheckRun(supervisorToken, "i5-check-main", """
                {"runScope":"ALL"}
                """)
                .andExpect(status().isCreated())
                .andReturnJson().get("data");
        Assertions.assertThat(sameKeyRun.get("id").asText()).isEqualTo(runId);

        postCheckRun(supervisorToken, "i5-check-main", """
                {"runScope":"BATTERY"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        JsonNode secondRun = postCheckRun(supervisorToken, "i5-check-dedup", """
                {"runScope":"ALL"}
                """)
                .andExpect(status().isCreated())
                .andReturnJson().get("data");
        Assertions.assertThat(secondRun.at("/resultSummary/dedupHitCount").asInt()).isGreaterThanOrEqualTo(6);
        Assertions.assertThat(dqAuditCount("DQ_ISSUE_REDISCOVERED", "SUCCESS")).isGreaterThanOrEqualTo(6);
    }

    @Test
    void validationMissingConfigAndPermissionsAreAudited() throws Exception {
        String supervisorToken = tokenFor("supervisor", "password");
        String adminToken = tokenFor("admin", "password");
        String recycleToken = tokenFor("recycle_operator", "password");

        int validationAuditBefore = dqAuditCount("DQ_REQUEST_VALIDATION_FAILED", "FAILED");
        mockMvc.perform(post("/api/v1/data-quality/check-runs")
                        .header("Authorization", bearer(supervisorToken))
                        .contentType("application/json")
                        .content("""
                                {"runScope":"ALL"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        Assertions.assertThat(dqAuditCount("DQ_REQUEST_VALIDATION_FAILED", "FAILED")).isGreaterThan(validationAuditBefore);

        postCheckRun(supervisorToken, "i5-rule-scope-invalid", """
                {"runScope":"INBOUND","requestedRuleCodes":["DQ-002"]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        int accessDeniedBefore = dqAuditCount("DQ_ACCESS_DENIED", "FORBIDDEN");
        postCheckRun(adminToken, "i5-admin-check-denied", """
                {"runScope":"ALL"}
                """)
                .andExpect(status().isForbidden());
        Assertions.assertThat(dqAuditCount("DQ_ACCESS_DENIED", "FORBIDDEN")).isGreaterThan(accessDeniedBefore);
        mockMvc.perform(get("/api/v1/data-quality/check-runs/1").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/data-quality/issues").header("Authorization", bearer(recycleToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        jdbcTemplate.update("DELETE FROM dq_enterprise_rule_config WHERE enterprise_id = 2 AND rule_code = 'DQ-007'");
        try {
            JsonNode failedRun = postCheckRun(tokenFor("enterprise_b_user", "password"), "i5-missing-config", """
                    {"runScope":"ALL"}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.runStatus").value("FAILED"))
                    .andReturnJson().get("data");
            Assertions.assertThat(resultCount(failedRun.get("id").asLong())).isZero();
        } finally {
            jdbcTemplate.update("""
                    INSERT INTO dq_enterprise_rule_config (
                      enterprise_id, rule_code, enabled_status, created_at, updated_at, version
                    )
                    VALUES (2, 'DQ-007', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                    ON DUPLICATE KEY UPDATE enabled_status = 'ENABLED'
                    """);
        }
    }

    @Test
    void crossEnterpriseRunsAndIssuesAreRejectedAndAudited() throws Exception {
        String enterpriseBToken = tokenFor("enterprise_b_user", "password");
        String enterpriseAToken = tokenFor("supervisor", "password");
        JsonNode enterpriseBRun = postCheckRun(enterpriseBToken, "i5-enterprise-b-run", """
                {"runScope":"BATTERY"}
                """)
                .andExpect(status().isCreated())
                .andReturnJson().get("data");
        long enterpriseBIssue = insertEnterpriseBIssue(enterpriseBRun.get("id").asLong());

        int checkDeniedBefore = dqAuditCount("DQ_CHECK_CROSS_ENTERPRISE_DENIED", "FORBIDDEN");
        mockMvc.perform(get("/api/v1/data-quality/check-runs/{runId}", enterpriseBRun.get("id").asText()).header("Authorization", bearer(enterpriseAToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
        Assertions.assertThat(dqAuditCount("DQ_CHECK_CROSS_ENTERPRISE_DENIED", "FORBIDDEN")).isGreaterThan(checkDeniedBefore);

        int issueDeniedBefore = dqAuditCount("DQ_ISSUE_CROSS_ENTERPRISE_DENIED", "FORBIDDEN");
        mockMvc.perform(get("/api/v1/data-quality/issues/{issueId}", enterpriseBIssue).header("Authorization", bearer(enterpriseAToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
        Assertions.assertThat(dqAuditCount("DQ_ISSUE_CROSS_ENTERPRISE_DENIED", "FORBIDDEN")).isGreaterThan(issueDeniedBefore);
    }

    private void insertRuleViolationFixtures(String suffix) {
        long blankBattery = battery(1L, 3L, "   ", "ORI-I5-BLANK-" + suffix, "REGISTERED", 1L);
        long candidateId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO battery_registration_candidate (
                  id, enterprise_id, original_code, battery_type, battery_chemistry, candidate_status,
                  submitted_by, submitted_at, created_at, updated_at, version
                )
                VALUES (?, 1, ?, 'PACK', 'UNKNOWN', 'PENDING_REVIEW', 3, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, candidateId, "ORI-I5-CANDIDATE-" + suffix);

        long noPassBattery = battery(1L, 4L, "BAT-I5-NOPASS-" + suffix, "ORI-I5-NOPASS-" + suffix, "IN_STOCK", 1L);
        inbound(noPassBattery, WAREHOUSE_A, LOCATION_A, "IB-I5-NOPASS-" + suffix);

        long mismatchBattery = battery(1L, 4L, "BAT-I5-MISMATCH-" + suffix, "ORI-I5-MISMATCH-" + suffix, "IN_STOCK", 1L);
        inbound(mismatchBattery, WAREHOUSE_A, LOCATION_A_MISMATCH, "IB-I5-MISMATCH-" + suffix);

        long traceBattery = battery(1L, 3L, "BAT-I5-TRACE-" + suffix, "ORI-I5-TRACE-" + suffix, "IN_STOCK", 1L);
        lifecycle(traceBattery, "BATTERY_REGISTERED", LocalDateTime.now().plusMinutes(10));
        lifecycle(traceBattery, "INBOUND_COMPLETED", LocalDateTime.now());

        long inventoryBattery = battery(1L, 4L, "BAT-I5-INVENTORY-" + suffix, "ORI-I5-INVENTORY-" + suffix, "IN_STOCK", 2L);
        long inboundId = inbound(inventoryBattery, WAREHOUSE_A, LOCATION_A, "IB-I5-INVENTORY-" + suffix);
        inventory(inventoryBattery, inboundId);
        Assertions.assertThat(blankBattery).isPositive();
    }

    private long battery(long enterpriseId, long userId, String traceCode, String originalCode, String lifecycleStatus, long responsibleEnterpriseId) {
        long batteryId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO battery (
                  id, enterprise_id, system_trace_code, original_code, battery_type, battery_chemistry,
                  current_responsible_enterprise_id, lifecycle_status, duplicate_status,
                  created_by, created_at, updated_by, updated_at, version
                )
                VALUES (?, ?, ?, ?, 'PACK', 'UNKNOWN', ?, ?, 'NORMAL', ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3), 0)
                """, batteryId, enterpriseId, traceCode, originalCode, responsibleEnterpriseId, lifecycleStatus, userId, userId);
        return batteryId;
    }

    private long inbound(long batteryId, long warehouseId, long locationId, String inboundNo) {
        long inboundId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO inbound_record (
                  id, enterprise_id, inbound_no, battery_id, warehouse_id, location_id,
                  inbound_status, inbound_by, inbound_at, created_at, version
                )
                VALUES (?, 1, ?, ?, ?, ?, 'EFFECTIVE', 4, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, inboundId, inboundNo, batteryId, warehouseId, locationId);
        return inboundId;
    }

    private void inventory(long batteryId, long inboundId) {
        long inventoryId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO inventory (
                  id, enterprise_id, battery_id, warehouse_id, location_id, inbound_record_id,
                  is_current, created_at, updated_at, version
                )
                VALUES (?, 1, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, inventoryId, batteryId, WAREHOUSE_A, LOCATION_A, inboundId);
    }

    private void lifecycle(long batteryId, String eventType, LocalDateTime occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO lifecycle_event (
                  id, enterprise_id, battery_id, event_type, event_name, from_status, to_status,
                  event_result, operator_user_id, occurred_at
                )
                VALUES (?, 1, ?, ?, ?, NULL, NULL, 'SUCCESS', 3, ?)
                """, idGenerator.nextId(), batteryId, eventType, eventType, occurredAt);
    }

    private long insertEnterpriseBIssue(long runId) {
        long resultId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO dq_check_result (
                  id, enterprise_id, run_id, rule_code, check_status, checked_object_count,
                  violation_count, rule_snapshot, started_at, completed_at
                )
                VALUES (?, 2, ?, 'DQ-002', 'VIOLATION', 1, 1, '{}', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, resultId, runId);
        long issueId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO dq_issue (
                  id, enterprise_id, issue_no, rule_code, object_type, object_id, object_identity,
                  object_key, title, description, severity, owner_role_code, issue_status,
                  discovered_run_id, discovered_result_id
                )
                VALUES (?, 2, ?, 'DQ-002', 'BATTERY', ?, ?, ?, 'DQ-002 企业B问题',
                        '企业B测试问题', 'MEDIUM', 'RECYCLE_OPERATOR', 'OPEN', ?, ?)
                """, issueId, "DQI-" + issueId, 200000001L, "B-" + issueId, "B-" + issueId, runId, resultId);
        return issueId;
    }

    private JsonResult postCheckRun(String token, String key, String body) throws Exception {
        return new JsonResult(mockMvc.perform(post("/api/v1/data-quality/check-runs")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body)));
    }

    private String tokenFor(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).get("data").get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private int configCount(long enterpriseId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dq_enterprise_rule_config
                WHERE enterprise_id = ?
                  AND rule_code IN ('DQ-001','DQ-002','DQ-003','DQ-004','DQ-005','DQ-006','DQ-007')
                """, Integer.class, enterpriseId);
    }

    private String enabledStatus(long enterpriseId, String ruleCode) {
        return jdbcTemplate.queryForObject("SELECT enabled_status FROM dq_enterprise_rule_config WHERE enterprise_id = ? AND rule_code = ?", String.class, enterpriseId, ruleCode);
    }

    private int dqAuditCount(String actionCode, String result) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dq_operation_audit WHERE action_code = ? AND result = ?", Integer.class, actionCode, result);
    }

    private int resultCount(long runId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dq_check_result WHERE run_id = ?", Integer.class, runId);
    }

    private final class JsonResult {
        private final org.springframework.test.web.servlet.ResultActions actions;

        private JsonResult(org.springframework.test.web.servlet.ResultActions actions) {
            this.actions = actions;
        }

        JsonResult andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        MvcResult andReturn() throws Exception {
            return actions.andReturn();
        }

        JsonNode andReturnJson() throws Exception {
            return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
    }
}
