package com.batteryrecycling.traceability.i2;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class I2BatchBatteryIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void createBatchValidatesRequiredFieldsAndReturnsDraft() throws Exception {
        String token = recycleToken();
        mockMvc.perform(post("/api/v1/recycle-batches")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-batch-missing")
                        .contentType("application/json")
                        .content("""
                                {"sourceType":"ENTERPRISE","sourceSubjectName":""}
                                """))
                .andExpect(status().isBadRequest());

        JsonNode batch = postJson("/api/v1/recycle-batches", token, "i2-batch-create-001", """
                {"sourceType":"ENTERPRISE","sourceSubjectName":"测试来源企业","handoverDate":"2026-09-29"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.batchNo").isNotEmpty())
                .andExpect(jsonPath("$.data.enterpriseId").value(1))
                .andReturnJson();

        Assertions.assertThat(batch.get("data").get("batteries")).isNotNull();
    }

    @Test
    @Order(2)
    void createAndUpdateDraftBatchThenRejectUpdateAfterSubmit() throws Exception {
        String token = recycleToken();
        long batchId = createBatch(token, "i2-batch-update-base", "可修改来源");

        mockMvc.perform(put("/api/v1/recycle-batches/{id}", batchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-batch-update-001")
                        .contentType("application/json")
                        .content("""
                                {"sourceType":"ENTERPRISE","sourceSubjectName":"已修改来源","handoverDate":"2026-09-30","remark":"草稿可修改"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceSubjectName").value("已修改来源"))
                .andExpect(jsonPath("$.data.version").value(1));

        long batteryId = createBattery(token, "i2-update-submit-battery", "ORI-I2-UPD").get("data").get("battery").get("id").asLong();
        addBattery(token, batchId, batteryId, "i2-update-add");
        submitBatch(token, batchId, "i2-update-submit");

        mockMvc.perform(put("/api/v1/recycle-batches/{id}", batchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-batch-update-conflict")
                        .contentType("application/json")
                        .content("""
                                {"sourceType":"ENTERPRISE","sourceSubjectName":"禁止修改","handoverDate":"2026-09-30"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    void batteryRegistrationDuplicateReviewAndTraceAreClosedLoop() throws Exception {
        String token = recycleToken();
        long firstBatteryId = createBattery(token, "i2-battery-first", "ORI-I2-DUP").get("data").get("battery").get("id").asLong();
        int lifecycleBefore = lifecycleCount(firstBatteryId);

        mockMvc.perform(post("/api/v1/batteries/duplicate-check")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"originalCode":"ORI-I2-DUP"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicated").value(true))
                .andExpect(jsonPath("$.data.matchedBatteryIds").isArray());
        Assertions.assertThat(lifecycleCount(firstBatteryId)).isEqualTo(lifecycleBefore);

        JsonNode duplicate = createBattery(token, "i2-battery-duplicate", "ORI-I2-DUP");
        duplicate.path("data").path("resultType").asText();
        Assertions.assertThat(duplicate.get("data").get("resultType").asText()).isEqualTo("DUPLICATE_REVIEW_REQUIRED");
        long candidateId = duplicate.get("data").get("candidateId").asLong();
        Assertions.assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lifecycle_event WHERE remark IS NULL AND event_type='BATTERY_REGISTERED' AND battery_id = ?", Integer.class, candidateId)).isZero();

        mockMvc.perform(post("/api/v1/battery-registration-candidates/{id}/duplicate-resolution", candidateId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-duplicate-same")
                        .contentType("application/json")
                        .content("""
                                {"reviewResult":"SAME_BATTERY","existingBatteryId":%d}
                                """.formatted(firstBatteryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstBatteryId));

        mockMvc.perform(post("/api/v1/battery-registration-candidates/{id}/duplicate-resolution", candidateId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-duplicate-same-again")
                        .contentType("application/json")
                        .content("""
                                {"reviewResult":"SAME_BATTERY","existingBatteryId":%d}
                                """.formatted(firstBatteryId)))
                .andExpect(status().isConflict());

        JsonNode secondDuplicate = createBattery(token, "i2-battery-duplicate-2", "ORI-I2-DUP");
        long secondCandidateId = secondDuplicate.get("data").get("candidateId").asLong();
        mockMvc.perform(post("/api/v1/battery-registration-candidates/{id}/duplicate-resolution", secondCandidateId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-duplicate-different-missing")
                        .contentType("application/json")
                        .content("""
                                {"reviewResult":"DIFFERENT_BATTERY"}
                                """))
                .andExpect(status().isBadRequest());
        String differentResponse = mockMvc.perform(post("/api/v1/battery-registration-candidates/{id}/duplicate-resolution", secondCandidateId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-duplicate-different")
                        .contentType("application/json")
                        .content("""
                                {"reviewResult":"DIFFERENT_BATTERY","duplicateReason":"人工核实为不同电池"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemTraceCode").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long differentBatteryId = objectMapper.readTree(differentResponse).get("data").get("id").asLong();
        Assertions.assertThat(lifecycleCount(differentBatteryId)).isGreaterThan(0);
    }

    @Test
    @Order(4)
    void addBatteryAndSubmitBatchAtomicallyMovesToPendingAcceptance() throws Exception {
        String token = recycleToken();
        long batchId = createBatch(token, "i2-submit-batch", "提交来源");
        long batteryId = createBattery(token, "i2-submit-battery", "ORI-I2-SUBMIT").get("data").get("battery").get("id").asLong();

        addBattery(token, batchId, batteryId, "i2-submit-add");
        addBattery(token, batchId, batteryId, "i2-submit-add-repeat");
        Assertions.assertThat(activeBatchRelationCount(batteryId)).isOne();

        JsonNode submitted = submitBatch(token, batchId, "i2-submit-ok");
        Assertions.assertThat(submitted.get("data").get("batchStatus").asText()).isEqualTo("PENDING_ACCEPTANCE");
        Assertions.assertThat(batteryStatus(batteryId)).isEqualTo("PENDING_ACCEPTANCE");
        Assertions.assertThat(batchSubmitEventCount(batteryId, batchId)).isOne();

        String repeated = mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", batchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-submit-ok"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assertions.assertThat(objectMapper.readTree(repeated).get("data").get("batchStatus").asText()).isEqualTo("PENDING_ACCEPTANCE");

        mockMvc.perform(get("/api/v1/batteries/{id}/trace", batteryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventName", hasItem("批次提交待验收")));
    }

    @Test
    @Order(5)
    void batchSubmitFailureKeepsStateAndEmptyBatchIsRejected() throws Exception {
        String token = recycleToken();
        long emptyBatchId = createBatch(token, "i2-empty-batch", "空批次来源");
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", emptyBatchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-empty-submit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BATCH_EMPTY"));
        Assertions.assertThat(batchStatus(emptyBatchId)).isEqualTo("DRAFT");
        Assertions.assertThat(idempotencyStatus("SUBMIT_RECYCLE_BATCH", "i2-empty-submit")).isEqualTo("FAILED");
    }

    @Test
    @Order(6)
    void tenantPermissionIdempotencyAndConcurrencyControlsAreEnforced() throws Exception {
        String recycleToken = recycleToken();
        String warehouseToken = warehouseToken();
        long foreignBatchId = insertForeignBatch();

        mockMvc.perform(get("/api/v1/recycle-batches/{id}", foreignBatchId)
                        .header("Authorization", bearer(recycleToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
        Assertions.assertThat(forbiddenAuditCount("BATCH_CROSS_ENTERPRISE_DENIED")).isGreaterThan(0);

        mockMvc.perform(post("/api/v1/recycle-batches")
                        .header("Authorization", bearer(warehouseToken))
                        .header("Idempotency-Key", "i2-warehouse-create")
                        .contentType("application/json")
                        .content("""
                                {"sourceType":"ENTERPRISE","sourceSubjectName":"无权来源","handoverDate":"2026-09-29"}
                                """))
                .andExpect(status().isForbidden());

        long batchA = createBatch(recycleToken, "i2-active-batch-a", "批次A");
        long batchB = createBatch(recycleToken, "i2-active-batch-b", "批次B");
        long batteryId = createBattery(recycleToken, "i2-active-battery", "ORI-I2-ACTIVE").get("data").get("battery").get("id").asLong();
        addBattery(recycleToken, batchA, batteryId, "i2-active-add-a");
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/batteries", batchB)
                        .header("Authorization", bearer(recycleToken))
                        .header("Idempotency-Key", "i2-active-add-b")
                        .contentType("application/json")
                        .content("""
                                {"batteryId":%d}
                                """.formatted(batteryId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/recycle-batches/{id}/batteries", batchA)
                        .header("Authorization", bearer(recycleToken))
                        .header("Idempotency-Key", "i2-active-add-a")
                        .contentType("application/json")
                        .content("""
                                {"batteryId":%d}
                                """.formatted(batteryId + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @Order(7)
    void concurrentRegistrationWithSameOriginalCodeDoesNotCreateTwoEffectiveBatteries() throws Exception {
        String token = recycleToken();
        String originalCode = "ORI-I2-CONCURRENT-" + System.currentTimeMillis();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        List<java.util.concurrent.Future<Integer>> responses = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            int index = i;
            responses.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/batteries")
                                .header("Authorization", bearer(token))
                                .header("Idempotency-Key", "i2-concurrent-battery-" + index)
                                .contentType("application/json")
                                .content("""
                                        {"originalCode":"%s","batteryType":"PACK","batteryChemistry":"UNKNOWN"}
                                        """.formatted(originalCode)))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        executor.shutdown();
        Assertions.assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        Set<Integer> statuses = ConcurrentHashMap.newKeySet();
        for (java.util.concurrent.Future<Integer> response : responses) {
            statuses.add(response.get());
        }
        Assertions.assertThat(statuses).contains(200);
        Integer effectiveBatteryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battery WHERE enterprise_id = 1 AND original_code = ?",
                Integer.class,
                originalCode);
        Integer pendingCandidateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battery_registration_candidate WHERE enterprise_id = 1 AND original_code = ? AND candidate_status = 'PENDING_REVIEW'",
                Integer.class,
                originalCode);
        Assertions.assertThat(effectiveBatteryCount).isEqualTo(1);
        Assertions.assertThat(pendingCandidateCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(8)
    void currentUserStillContainsConfirmedI1Permissions() throws Exception {
        mockMvc.perform(get("/api/v1/auth/current-user")
                        .header("Authorization", bearer(recycleToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", hasItem("batch:create")))
                .andExpect(jsonPath("$.data.permissions", hasItem("battery:duplicate:resolve")))
                .andExpect(jsonPath("$.data.permissions", not(hasItem("permission:manage"))));
    }

    private JsonResult postJson(String path, String token, String key, String body) throws Exception {
        return new JsonResult(mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body)));
    }

    private long createBatch(String token, String key, String sourceName) throws Exception {
        return postJson("/api/v1/recycle-batches", token, key, """
                {"sourceType":"ENTERPRISE","sourceSubjectName":"%s","handoverDate":"2026-09-29"}
                """.formatted(sourceName))
                .andExpect(status().isOk())
                .andReturnJson()
                .get("data").get("id").asLong();
    }

    private JsonNode createBattery(String token, String key, String originalCode) throws Exception {
        return postJson("/api/v1/batteries", token, key, """
                {"originalCode":"%s","batteryType":"PACK","batteryChemistry":"UNKNOWN"}
                """.formatted(originalCode))
                .andExpect(status().isOk())
                .andReturnJson();
    }

    private void addBattery(String token, long batchId, long batteryId, String key) throws Exception {
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/batteries", batchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("""
                                {"batteryId":%d}
                                """.formatted(batteryId)))
                .andExpect(status().isOk());
    }

    private JsonNode submitBatch(String token, long batchId, String key) throws Exception {
        String response = mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", batchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private long insertForeignBatch() {
        long id = 880000000000000001L;
        jdbcTemplate.update("""
                INSERT INTO recycle_batch (
                  id, enterprise_id, batch_no, source_type, source_subject_name, handover_date,
                  batch_status, created_by, created_at, updated_at, version
                )
                VALUES (?, 2, ?, 'ENTERPRISE', '企业B来源', '2026-09-29', 'DRAFT', 6, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                ON DUPLICATE KEY UPDATE id = id
                """, id, "RB-FOREIGN-I2");
        return id;
    }

    private String recycleToken() throws Exception {
        return tokenFor("recycle_operator", "password");
    }

    private String warehouseToken() throws Exception {
        return tokenFor("warehouse_admin", "password");
    }

    private String tokenFor(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private int lifecycleCount(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lifecycle_event WHERE battery_id = ?", Integer.class, batteryId);
    }

    private int activeBatchRelationCount(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recycle_batch_battery WHERE battery_id = ? AND relation_status = 'ACTIVE'", Integer.class, batteryId);
    }

    private String batteryStatus(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT lifecycle_status FROM battery WHERE id = ?", String.class, batteryId);
    }

    private String batchStatus(long batchId) {
        return jdbcTemplate.queryForObject("SELECT batch_status FROM recycle_batch WHERE id = ?", String.class, batchId);
    }

    private int batchSubmitEventCount(long batteryId, long batchId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lifecycle_event WHERE battery_id = ? AND batch_id = ? AND event_type = 'BATCH_SUBMITTED'", Integer.class, batteryId, batchId);
    }

    private String idempotencyStatus(String operationCode, String idempotencyKey) {
        return jdbcTemplate.queryForObject("SELECT process_status FROM idempotency_record WHERE operation_code = ? AND idempotency_key = ?", String.class, operationCode, idempotencyKey);
    }

    private int forbiddenAuditCount(String actionCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND result = 'FORBIDDEN'", Integer.class, actionCode);
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

        JsonNode andReturnJson() throws Exception {
            return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }
}
