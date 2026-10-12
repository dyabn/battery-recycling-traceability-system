package com.batteryrecycling.traceability.i5;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
                .andExpect(jsonPath("$.data[*].id", everyItem(matchesPattern("^[0-9]+$"))))
                .andExpect(jsonPath("$.data[*].dataStandardMetadata").exists())
                .andExpect(jsonPath("$.data[*].involvedFields").exists())
                .andExpect(jsonPath("$.data[*].remediationGuidance").exists())
                .andExpect(jsonPath("$.data[*].allowExemption", everyItem(equalTo(false))))
                .andExpect(jsonPath("$.data[*].enabledStatus", everyItem(equalTo("ENABLED"))));

        Assertions.assertThat(configCount(1L)).isEqualTo(7);
        Assertions.assertThat(configCount(2L)).isEqualTo(7);

        jdbcTemplate.update("""
                UPDATE dq_enterprise_rule_config
                SET enabled_status = 'DISABLED', toggled_reason = 'test keep disabled'
                WHERE enterprise_id = 1 AND rule_code = 'DQ-007'
                """);
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V7__backfill_data_governance_rule_configs.sql"));
        }
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

        String traceId = "trace-i5-main";
        JsonNode firstRun = postCheckRun(supervisorToken, "i5-check-main", traceId, """
                {"runScope":"ALL"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.traceId").value(traceId))
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
        assertRuleIssueCount("DQ-002", 1);
        assertRuleIssueCount("DQ-003", 1);
        assertRuleIssueCount("DQ-004", 1);
        assertRuleIssueCount("DQ-005", 1);
        assertRuleIssueCount("DQ-006", 1);
        assertRuleIssueCount("DQ-007", 1);
        Assertions.assertThat(issueCountForObject("DQ-004", "IB-I5-VALID-MAIN")).isZero();
        Map<String, Object> createdAudit = latestDqAudit("DQ_ISSUE_CREATED", "SUCCESS");
        Assertions.assertThat(createdAudit.get("before_state")).isNull();
        Assertions.assertThat(createdAudit.get("after_state")).isEqualTo("OPEN");
        Assertions.assertThat(createdAudit.get("trace_id")).isEqualTo(traceId);
        Assertions.assertThat(createdAudit.get("idempotency_key_hash")).asString().hasSize(64);

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
        Map<String, Object> rediscoveredAudit = latestDqAudit("DQ_ISSUE_REDISCOVERED", "SUCCESS");
        Assertions.assertThat(rediscoveredAudit.get("before_state")).isEqualTo("OPEN");
        Assertions.assertThat(rediscoveredAudit.get("after_state")).isEqualTo("OPEN");
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
        mockMvc.perform(get("/api/v1/data-quality/check-runs/999999999999").header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/data-quality/issues").param("status", "NOT_A_STATUS").header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/data-quality/issues").header("Authorization", bearer(recycleToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        String warehouseToken = tokenFor("warehouse_admin", "password");
        long recycleIssueId = insertAssignedIssue("DQ-002", "RECYCLE_OPERATOR", 3L, "RECYCLE-ASSIGNED");
        long warehouseIssueId = insertAssignedIssue("DQ-007", "WAREHOUSE_ADMIN", 4L, "WAREHOUSE-ASSIGNED");
        mockMvc.perform(get("/api/v1/data-quality/issues").header("Authorization", bearer(recycleToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(String.valueOf(recycleIssueId))))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(String.valueOf(warehouseIssueId)))));
        mockMvc.perform(get("/api/v1/data-quality/issues/{issueId}", warehouseIssueId).header("Authorization", bearer(recycleToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/data-quality/issues").header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(String.valueOf(warehouseIssueId))))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(String.valueOf(recycleIssueId)))));
        mockMvc.perform(get("/api/v1/data-quality/issues/{issueId}", warehouseIssueId).header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(warehouseIssueId)));

        jdbcTemplate.update("DELETE FROM dq_enterprise_rule_config WHERE enterprise_id = 2 AND rule_code = 'DQ-007'");
        try {
            JsonNode failedRun = postCheckRun(tokenFor("enterprise_b_user", "password"), "i5-missing-config", """
                    {"runScope":"ALL"}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.runStatus").value("FAILED"))
                    .andReturnJson().get("data");
            Assertions.assertThat(resultCount(failedRun.get("id").asLong())).isZero();
            Assertions.assertThat(issueCountForRun(failedRun.get("id").asLong())).isZero();
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

    @Test
    void ruleProcessorsCoverDuplicateClosedCandidateAndEnterpriseIsolation() throws Exception {
        String supervisorToken = tokenFor("supervisor", "password");
        verifyNoViolationCheckCompletesWithoutIssues(supervisorToken);
        verifyDq001DuplicateTraceCode(supervisorToken);
        verifyDq003ClosedCandidateWithoutReview(supervisorToken);
        verifyDq004AllowsAcceptedInboundWithoutInboundCompletedEvent(supervisorToken);
        verifyDq005AndDq007RejectCrossEnterpriseReferencesWithoutLeakingOtherEnterpriseDetails(supervisorToken);
    }

    @Test
    void partialWriteFailureRollsBackResultsIssuesAndSuccessAudit() throws Exception {
        String supervisorToken = tokenFor("supervisor", "password");
        insertRuleViolationFixtures("ROLLBACK");
        String originalName = jdbcTemplate.queryForObject("SELECT rule_name FROM dq_rule_definition WHERE rule_code = 'DQ-003'", String.class);
        String traceId = "trace-i5-rollback";
        try {
            jdbcTemplate.update("UPDATE dq_rule_definition SET rule_name = ? WHERE rule_code = 'DQ-003'", "X".repeat(128));
            JsonNode failedRun = postCheckRun(supervisorToken, "i5-partial-rollback", traceId, """
                    {"runScope":"BATTERY","requestedRuleCodes":["DQ-002","DQ-003"]}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.runStatus").value("FAILED"))
                    .andReturnJson().get("data");
            long runId = failedRun.get("id").asLong();
            Assertions.assertThat(resultCount(runId)).isZero();
            Assertions.assertThat(issueCountForRun(runId)).isZero();
            Assertions.assertThat(dqAuditCountByTrace("DQ_ISSUE_CREATED", traceId)).isZero();
            Assertions.assertThat(dqAuditCountByTrace("DQ_CHECK_COMPLETED", traceId)).isZero();
            Assertions.assertThat(dqAuditCountForRun("DQ_CHECK_FAILED", runId)).isGreaterThanOrEqualTo(1);
        } finally {
            jdbcTemplate.update("UPDATE dq_rule_definition SET rule_name = ? WHERE rule_code = 'DQ-003'", originalName);
        }
    }

    @Test
    void concurrentChecksDeduplicateOpenIssues() throws Exception {
        String supervisorToken = tokenFor("supervisor", "password");
        long batteryId = battery(1L, 3L, "BAT-I5-CONCURRENT", "ORI-I5-CONCURRENT", "REGISTERED", 1L);
        jdbcTemplate.update("UPDATE battery SET battery_chemistry = '' WHERE id = ?", batteryId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> runConcurrentCheck(supervisorToken, "i5-concurrent-a", ready, start));
            Future<String> second = executor.submit(() -> runConcurrentCheck(supervisorToken, "i5-concurrent-b", ready, start));
            Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Assertions.assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsOnly("COMPLETED", "COMPLETED");
            Assertions.assertThat(openIssueCount("DQ-002", String.valueOf(batteryId))).isEqualTo(1);
            Assertions.assertThat(dqAuditCount("DQ_ISSUE_REDISCOVERED", "SUCCESS")).isGreaterThanOrEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertRuleViolationFixtures(String suffix) {
        long coreMissingBattery = battery(1L, 3L, "BAT-I5-CORE-MISSING-" + suffix, "ORI-I5-CORE-MISSING-" + suffix, "REGISTERED", 1L);
        jdbcTemplate.update("UPDATE battery SET battery_chemistry = '' WHERE id = ?", coreMissingBattery);
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

        long validInboundBattery = battery(1L, 4L, "BAT-I5-VALID-" + suffix, "ORI-I5-VALID-" + suffix, "IN_STOCK", 1L);
        acceptancePass(validInboundBattery);
        lifecycle(validInboundBattery, "ACCEPTANCE_PASSED", "PENDING_ACCEPTANCE", "ACCEPTED_PENDING_INBOUND", LocalDateTime.now().minusMinutes(5));
        inbound(validInboundBattery, WAREHOUSE_A, LOCATION_A, "IB-I5-VALID-" + suffix);
        lifecycle(validInboundBattery, "INBOUND_COMPLETED", "ACCEPTED_PENDING_INBOUND", "IN_STOCK", LocalDateTime.now().plusSeconds(1));

        long mismatchBattery = battery(1L, 4L, "BAT-I5-MISMATCH-" + suffix, "ORI-I5-MISMATCH-" + suffix, "IN_STOCK", 1L);
        inbound(mismatchBattery, WAREHOUSE_A, LOCATION_A_MISMATCH, "IB-I5-MISMATCH-" + suffix);

        long traceBattery = battery(1L, 3L, "BAT-I5-TRACE-" + suffix, "ORI-I5-TRACE-" + suffix, "IN_STOCK", 1L);
        lifecycle(traceBattery, "BATTERY_REGISTERED", LocalDateTime.now().plusMinutes(10));
        lifecycle(traceBattery, "INBOUND_COMPLETED", LocalDateTime.now());

        long inventoryBattery = battery(1L, 4L, "BAT-I5-INVENTORY-" + suffix, "ORI-I5-INVENTORY-" + suffix, "IN_STOCK", 2L);
        long inboundId = inbound(inventoryBattery, WAREHOUSE_A, LOCATION_A, "IB-I5-INVENTORY-" + suffix);
        inventory(inventoryBattery, inboundId);
        Assertions.assertThat(coreMissingBattery).isPositive();
    }

    private void verifyDq001DuplicateTraceCode(String supervisorToken) throws Exception {
        String duplicateTrace = "BAT-I5-DUPLICATE-TRACE";
        try {
            if (traceIndexExists()) {
                jdbcTemplate.execute("ALTER TABLE battery DROP INDEX uk_battery_trace_code");
            }
            battery(1L, 3L, duplicateTrace, "ORI-I5-DUP-TRACE-A", "REGISTERED", 1L);
            battery(1L, 3L, duplicateTrace, "ORI-I5-DUP-TRACE-B", "REGISTERED", 1L);
            postCheckRun(supervisorToken, "i5-dq001-duplicate", """
                    {"runScope":"BATTERY","requestedRuleCodes":["DQ-001"]}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.runStatus").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.issueCount").value(greaterThanOrEqualTo(1)));
            Assertions.assertThat(issueCountForObject("DQ-001", duplicateTrace)).isGreaterThanOrEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM battery WHERE system_trace_code = ?", duplicateTrace);
            if (!traceIndexExists()) {
                jdbcTemplate.execute("CREATE UNIQUE INDEX uk_battery_trace_code ON battery (system_trace_code)");
            }
            Assertions.assertThat(traceIndexExists()).as("battery trace unique index restored").isTrue();
        }
    }

    private void verifyNoViolationCheckCompletesWithoutIssues(String supervisorToken) throws Exception {
        JsonNode run = postCheckRun(supervisorToken, "i5-no-violation-dq001", """
                {"runScope":"BATTERY","requestedRuleCodes":["DQ-001"]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.issueCount").value(0))
                .andReturnJson().get("data");
        Assertions.assertThat(issueCountForRun(run.get("id").asLong())).isZero();
        Assertions.assertThat(resultStatuses(run.get("id").asLong())).containsOnly("PASS");
    }

    private void verifyDq003ClosedCandidateWithoutReview(String supervisorToken) throws Exception {
        long candidateId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO battery_registration_candidate (
                  id, enterprise_id, original_code, battery_type, battery_chemistry, candidate_status,
                  submitted_by, submitted_at, closed_at, created_at, updated_at, version
                )
                VALUES (?, 1, ?, 'PACK', 'UNKNOWN', 'CLOSED_DIFFERENT', 3, CURRENT_TIMESTAMP(3),
                        CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, candidateId, "ORI-I5-CLOSED-NO-REVIEW");
        postCheckRun(supervisorToken, "i5-dq003-closed-missing-review", """
                {"runScope":"BATTERY","requestedRuleCodes":["DQ-003"]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.issueCount").value(greaterThanOrEqualTo(1)));
        Assertions.assertThat(issueCountForObject("DQ-003", String.valueOf(candidateId))).isGreaterThanOrEqualTo(1);
    }

    private void verifyDq004AllowsAcceptedInboundWithoutInboundCompletedEvent(String supervisorToken) throws Exception {
        long batteryId = battery(1L, 4L, "BAT-I5-DQ004-NO-INBOUND-EVENT", "ORI-I5-DQ004-NO-INBOUND-EVENT", "IN_STOCK", 1L);
        acceptancePass(batteryId);
        lifecycle(batteryId, "ACCEPTANCE_PASSED", "PENDING_ACCEPTANCE", "ACCEPTED_PENDING_INBOUND", LocalDateTime.now().minusMinutes(5));
        inbound(batteryId, WAREHOUSE_A, LOCATION_A, "IB-I5-DQ004-NO-INBOUND-EVENT");
        postCheckRun(supervisorToken, "i5-dq004-no-inbound-event", """
                {"runScope":"INBOUND","requestedRuleCodes":["DQ-004"]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runStatus").value("COMPLETED"));
        Assertions.assertThat(issueCountForObject("DQ-004", "IB-I5-DQ004-NO-INBOUND-EVENT")).isZero();
    }

    private void verifyDq005AndDq007RejectCrossEnterpriseReferencesWithoutLeakingOtherEnterpriseDetails(String supervisorToken) throws Exception {
        long[] enterpriseBWarehouse = createEnterpriseBWarehouseAndLocation();
        long inboundBattery = battery(1L, 4L, "BAT-I5-DQ005-CROSS-WH", "ORI-I5-DQ005-CROSS-WH", "IN_STOCK", 1L);
        long crossWarehouseInboundId = inbound(inboundBattery, enterpriseBWarehouse[0], enterpriseBWarehouse[1], "IB-I5-DQ005-CROSS-WH");
        postCheckRun(supervisorToken, "i5-dq005-cross-enterprise", """
                {"runScope":"INBOUND","requestedRuleCodes":["DQ-005"]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runStatus").value("COMPLETED"));
        Assertions.assertThat(issueCountForObject("DQ-005", "IB-I5-DQ005-CROSS-WH")).isGreaterThanOrEqualTo(1);

        long otherEnterpriseBattery = battery(2L, 6L, "BAT-I5-SECRET-ENTERPRISE-B", "ORI-I5-SECRET-ENTERPRISE-B", "IN_STOCK", 2L);
        long crossEnterpriseInventoryId = inventory(otherEnterpriseBattery, crossWarehouseInboundId);
        postCheckRun(supervisorToken, "i5-dq007-cross-enterprise", """
                {"runScope":"INVENTORY","requestedRuleCodes":["DQ-007"]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runStatus").value("COMPLETED"));
        String description = issueDescriptionForObject("DQ-007", String.valueOf(crossEnterpriseInventoryId));
        Assertions.assertThat(description).contains("不属于本企业");
        Assertions.assertThat(description).doesNotContain("BAT-I5-SECRET-ENTERPRISE-B");
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

    private void acceptancePass(long batteryId) {
        long batchId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO recycle_batch (
                  id, enterprise_id, batch_no, source_type, source_subject_name,
                  handover_date, handover_location, handover_person, batch_status,
                  submitted_at, created_by, created_at, updated_by, updated_at, version
                )
                VALUES (?, 1, ?, 'SOCIAL_RECYCLE', '测试来源', CURRENT_DATE, '测试地点', '测试员',
                        'COMPLETED', CURRENT_TIMESTAMP(3), 3, CURRENT_TIMESTAMP(3), 3, CURRENT_TIMESTAMP(3), 0)
                """, batchId, "RB-I5-" + batchId);
        jdbcTemplate.update("""
                INSERT INTO acceptance_record (
                  id, enterprise_id, battery_id, batch_id, acceptance_result,
                  identity_check_result, appearance_check_result, document_check_result,
                  acceptance_note, accepted_by, accepted_at, created_at, version
                )
                VALUES (?, 1, ?, ?, 'PASS', 'PASS', 'PASS', 'PASS', NULL, 3,
                        CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, idGenerator.nextId(), batteryId, batchId);
    }

    private long inventory(long batteryId, long inboundId) {
        long inventoryId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO inventory (
                  id, enterprise_id, battery_id, warehouse_id, location_id, inbound_record_id,
                  is_current, created_at, updated_at, version
                )
                VALUES (?, 1, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, inventoryId, batteryId, WAREHOUSE_A, LOCATION_A, inboundId);
        return inventoryId;
    }

    private void lifecycle(long batteryId, String eventType, LocalDateTime occurredAt) {
        lifecycle(batteryId, eventType, null, null, occurredAt);
    }

    private void lifecycle(long batteryId, String eventType, String fromStatus, String toStatus, LocalDateTime occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO lifecycle_event (
                  id, enterprise_id, battery_id, event_type, event_name, from_status, to_status,
                  event_result, operator_user_id, occurred_at
                )
                VALUES (?, 1, ?, ?, ?, ?, ?, 'SUCCESS', 3, ?)
                """, idGenerator.nextId(), batteryId, eventType, eventType, fromStatus, toStatus, occurredAt);
    }

    private long insertEnterpriseBIssue(long runId) {
        Long resultId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM dq_check_result
                WHERE enterprise_id = 2
                  AND run_id = ?
                  AND rule_code = 'DQ-002'
                """, Long.class, runId);
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

    private long insertAssignedIssue(String ruleCode, String ownerRoleCode, long assignedUserId, String suffix) {
        long runId = idGenerator.nextId();
        long resultId = idGenerator.nextId();
        long issueId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO dq_check_run (
                  id, enterprise_id, run_no, run_scope, run_status, requested_rule_codes,
                  executed_rule_codes, checked_object_count, checked_rule_count, issue_count,
                  result_summary, started_by, started_at, completed_at, trace_id, version
                )
                VALUES (?, 1, ?, 'BATTERY', 'COMPLETED', JSON_ARRAY(?), JSON_ARRAY(?), 1, 1, 1,
                        JSON_OBJECT('passRuleCount', 0, 'violationRuleCount', 1, 'newIssueCount', 1, 'dedupHitCount', 0),
                        2, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), ?, 0)
                """, runId, "DQR-" + runId, ruleCode, ruleCode, "trace-i5-assigned-" + suffix);
        jdbcTemplate.update("""
                INSERT INTO dq_check_result (
                  id, enterprise_id, run_id, rule_code, check_status, checked_object_count,
                  violation_count, rule_snapshot, started_at, completed_at
                )
                VALUES (?, 1, ?, ?, 'VIOLATION', 1, 1, JSON_OBJECT('ruleCode', ?),
                        CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, resultId, runId, ruleCode, ruleCode);
        jdbcTemplate.update("""
                INSERT INTO dq_issue (
                  id, enterprise_id, issue_no, rule_code, object_type, object_id, object_identity,
                  object_key, title, description, severity, owner_role_code, issue_status,
                  assigned_to_user_id, discovered_run_id, discovered_result_id
                )
                VALUES (?, 1, ?, ?, 'BATTERY', ?, ?, ?, ?, ?, 'MEDIUM', ?, 'OPEN', ?, ?, ?)
                """,
                issueId,
                "DQI-" + issueId,
                ruleCode,
                issueId,
                "ASSIGNED-" + suffix,
                "ASSIGNED-" + suffix,
                ruleCode + " assigned issue",
                "assigned issue " + suffix,
                ownerRoleCode,
                assignedUserId,
                runId,
                resultId);
        return issueId;
    }

    private JsonResult postCheckRun(String token, String key, String body) throws Exception {
        return new JsonResult(mockMvc.perform(post("/api/v1/data-quality/check-runs")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body)));
    }

    private JsonResult postCheckRun(String token, String key, String traceId, String body) throws Exception {
        return new JsonResult(mockMvc.perform(post("/api/v1/data-quality/check-runs")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .header("X-Trace-Id", traceId)
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

    private int issueCountForRun(long runId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dq_issue WHERE discovered_run_id = ?", Integer.class, runId);
    }

    private int dqAuditCountForRun(String actionCode, long runId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dq_operation_audit WHERE action_code = ? AND object_id = ?", Integer.class, actionCode, runId);
    }

    private int dqAuditCountByTrace(String actionCode, String traceId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dq_operation_audit WHERE action_code = ? AND trace_id = ?", Integer.class, actionCode, traceId);
    }

    private void assertRuleIssueCount(String ruleCode, int expectedMinimum) {
        Assertions.assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dq_issue WHERE enterprise_id = 1 AND rule_code = ?", Integer.class, ruleCode))
                .isGreaterThanOrEqualTo(expectedMinimum);
    }

    private int issueCountForObject(String ruleCode, String objectKey) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dq_issue WHERE enterprise_id = 1 AND rule_code = ? AND object_key = ?", Integer.class, ruleCode, objectKey);
    }

    private int openIssueCount(String ruleCode, String objectIdentity) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dq_issue WHERE enterprise_id = 1 AND rule_code = ? AND object_identity = ? AND issue_status = 'OPEN'", Integer.class, ruleCode, objectIdentity);
    }

    private List<String> resultStatuses(long runId) {
        return jdbcTemplate.queryForList("SELECT check_status FROM dq_check_result WHERE run_id = ? ORDER BY rule_code", String.class, runId);
    }

    private String issueDescriptionForObject(String ruleCode, String objectKey) {
        return jdbcTemplate.queryForObject("SELECT description FROM dq_issue WHERE enterprise_id = 1 AND rule_code = ? AND object_key = ? ORDER BY id DESC LIMIT 1", String.class, ruleCode, objectKey);
    }

    private boolean traceIndexExists() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'battery'
                  AND index_name = 'uk_battery_trace_code'
                """, Integer.class);
        return count != null && count > 0;
    }

    private long[] createEnterpriseBWarehouseAndLocation() {
        long warehouseId = idGenerator.nextId();
        long locationId = idGenerator.nextId();
        String warehouseCode = "WH-" + String.format("%03d", 200 + (int) (warehouseId % 700));
        String locationCode = warehouseCode + "-A01-R01-L01";
        jdbcTemplate.update("""
                INSERT INTO warehouse (
                  id, enterprise_id, warehouse_code, warehouse_name, enabled_status,
                  created_by, created_at, updated_by, updated_at, version
                )
                VALUES (?, 2, ?, 'B企业启用仓库', 'ENABLED', 6,
                        CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0)
                """, warehouseId, warehouseCode);
        jdbcTemplate.update("""
                INSERT INTO warehouse_location (
                  id, enterprise_id, warehouse_id, location_code, enabled_status,
                  created_by, created_at, updated_by, updated_at, version
                )
                VALUES (?, 2, ?, ?, 'ENABLED', 6,
                        CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0)
                """, locationId, warehouseId, locationCode);
        return new long[] {warehouseId, locationId};
    }

    private Map<String, Object> latestDqAudit(String actionCode, String result) {
        return jdbcTemplate.queryForMap("""
                SELECT before_state, after_state, trace_id, idempotency_key_hash
                FROM dq_operation_audit
                WHERE action_code = ? AND result = ?
                ORDER BY operated_at DESC, id DESC
                LIMIT 1
                """, actionCode, result);
    }

    private String runConcurrentCheck(String token, String key, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        Assertions.assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        JsonNode response = postCheckRun(token, key, """
                {"runScope":"BATTERY","requestedRuleCodes":["DQ-002"]}
                """)
                .andExpect(status().isCreated())
                .andReturnJson();
        return response.at("/data/runStatus").asText();
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
