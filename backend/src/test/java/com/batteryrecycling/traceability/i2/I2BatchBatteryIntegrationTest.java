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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.MvcResult;

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

    private long manualId = 880000100000000000L;

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
        int batteryBefore = totalBatteryCount();
        int candidateBefore = totalCandidateCount();
        int reviewBefore = totalDuplicateReviewCount();

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
        Assertions.assertThat(totalBatteryCount()).isEqualTo(batteryBefore);
        Assertions.assertThat(totalCandidateCount()).isEqualTo(candidateBefore);
        Assertions.assertThat(totalDuplicateReviewCount()).isEqualTo(reviewBefore);

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

        long invalidSourceBatchId = createBatch(token, "i2-invalid-source-batch", "异常来源");
        long invalidSourceBatteryId = createBattery(token, "i2-invalid-source-battery", "ORI-I2-INVALID-SOURCE").get("data").get("battery").get("id").asLong();
        addBattery(token, invalidSourceBatchId, invalidSourceBatteryId, "i2-invalid-source-add");
        jdbcTemplate.update("UPDATE recycle_batch SET source_subject_name = '' WHERE id = ?", invalidSourceBatchId);
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", invalidSourceBatchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-invalid-source-submit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BATCH_REQUIRED_FIELD_MISSING"));
        Assertions.assertThat(batchStatus(invalidSourceBatchId)).isEqualTo("DRAFT");
        Assertions.assertThat(batteryStatus(invalidSourceBatteryId)).isEqualTo("REGISTERED");
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

        mockMvc.perform(get("/api/v1/recycle-batches")
                        .header("Authorization", bearer(recycleToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(foreignBatchId))));

        mockMvc.perform(post("/api/v1/recycle-batches")
                        .header("Authorization", bearer(tokenFor("admin", "password")))
                        .header("Idempotency-Key", "i2-admin-create-denied")
                        .contentType("application/json")
                        .content("""
                                {"sourceType":"ENTERPRISE","sourceSubjectName":"系统管理员无权创建","handoverDate":"2026-09-29"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/batteries")
                        .header("Authorization", bearer(tokenFor("supervisor", "password")))
                        .header("Idempotency-Key", "i2-supervisor-battery-denied")
                        .contentType("application/json")
                        .content("""
                                {"originalCode":"ORI-I2-SUP-DENIED","batteryType":"PACK","batteryChemistry":"UNKNOWN"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/recycle-batches")
                        .header("Authorization", bearer(warehouseToken))
                        .header("Idempotency-Key", "i2-warehouse-create")
                        .contentType("application/json")
                        .content("""
                                {"sourceType":"ENTERPRISE","sourceSubjectName":"无权来源","handoverDate":"2026-09-29"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", foreignBatchId)
                        .header("Authorization", bearer(warehouseToken))
                        .header("Idempotency-Key", "i2-warehouse-submit-denied"))
                .andExpect(status().isForbidden());

        String foreignOriginalCode = "ORI-I2-FOREIGN-" + System.nanoTime();
        long foreignBatteryId = insertForeignBattery(foreignOriginalCode);
        mockMvc.perform(post("/api/v1/batteries/duplicate-check")
                        .header("Authorization", bearer(recycleToken))
                        .contentType("application/json")
                        .content("""
                                {"originalCode":"%s"}
                                """.formatted(foreignOriginalCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicated").value(false))
                .andExpect(jsonPath("$.data.matchedBatteryIds").isEmpty());
        long ownCandidateId = insertCandidate(1L, foreignOriginalCode);
        mockMvc.perform(post("/api/v1/battery-registration-candidates/{id}/duplicate-resolution", ownCandidateId)
                        .header("Authorization", bearer(recycleToken))
                        .header("Idempotency-Key", "i2-foreign-existing-denied")
                        .contentType("application/json")
                        .content("""
                                {"reviewResult":"SAME_BATTERY","existingBatteryId":%d}
                                """.formatted(foreignBatteryId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
        long foreignCandidateId = insertCandidate(2L, foreignOriginalCode);
        mockMvc.perform(post("/api/v1/battery-registration-candidates/{id}/duplicate-resolution", foreignCandidateId)
                        .header("Authorization", bearer(recycleToken))
                        .header("Idempotency-Key", "i2-foreign-candidate-denied")
                        .contentType("application/json")
                        .content("""
                                {"reviewResult":"DIFFERENT_BATTERY","duplicateReason":"跨企业候选"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));

        long batchA = createBatch(recycleToken, "i2-active-batch-a", "批次A");
        long batchB = createBatch(recycleToken, "i2-active-batch-b", "批次B");
        long batteryId = createBattery(recycleToken, "i2-active-battery", "ORI-I2-ACTIVE").get("data").get("battery").get("id").asLong();
        int successBefore = successAuditCountByDetail("BATTERY_ADDED_TO_BATCH", "batteryId=" + batteryId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> first = executor.submit(concurrentAddBattery(recycleToken, "i2-active-add-a", batchA, batteryId, ready, start));
        Future<Integer> second = executor.submit(concurrentAddBattery(recycleToken, "i2-active-add-b", batchB, batteryId, ready, start));
        Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        Assertions.assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        executor.shutdownNow();
        Assertions.assertThat(activeBatchRelationCount(batteryId)).isOne();
        Assertions.assertThat(successAuditCountByDetail("BATTERY_ADDED_TO_BATCH", "batteryId=" + batteryId)).isEqualTo(successBefore + 1);

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
    void repeatedRegistrationWithSameOriginalCodeDoesNotCreateTwoEffectiveBatteriesWithoutReview() throws Exception {
        String token = recycleToken();
        String originalCode = "ORI-I2-REPEAT-" + System.currentTimeMillis();

        createBattery(token, "i2-repeat-battery-1", originalCode)
                .get("data").get("battery").get("id").asLong();
        int effectiveBefore = batteryCountByOriginal(originalCode);
        JsonNode second = createBattery(token, "i2-repeat-battery-2", originalCode);
        Assertions.assertThat(second.get("data").get("resultType").asText()).isEqualTo("DUPLICATE_REVIEW_REQUIRED");

        Integer effectiveBatteryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battery WHERE enterprise_id = 1 AND original_code = ?",
                Integer.class,
                originalCode);
        Integer pendingCandidateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battery_registration_candidate WHERE enterprise_id = 1 AND original_code = ? AND candidate_status = 'PENDING_REVIEW'",
                Integer.class,
                originalCode);
        Assertions.assertThat(effectiveBefore).isOne();
        Assertions.assertThat(effectiveBatteryCount).isEqualTo(effectiveBefore);
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

    @Test
    @Order(9)
    void concurrentBatchCreationKeepsBatchNumbersUnique() throws Exception {
        String token = recycleToken();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JsonNode>> responses = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int index = i;
            responses.add(executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return createBatchNode(token, "i2-concurrent-batch-" + index, "并发来源" + index);
            }));
        }
        Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<String> batchNumbers = new ArrayList<>();
        for (Future<JsonNode> response : responses) {
            batchNumbers.add(response.get(20, TimeUnit.SECONDS).get("data").get("batchNo").asText());
        }
        executor.shutdownNow();
        Assertions.assertThat(batchNumbers).doesNotHaveDuplicates();
    }

    @Test
    @Order(10)
    void registrationValidationAndOriginalCodeLengthBoundaryAreEnforced() throws Exception {
        String token = recycleToken();
        JsonNode noOriginalCode = postJson("/api/v1/batteries", token, "i2-no-original-code", """
                {"batteryType":"PACK","batteryChemistry":"UNKNOWN"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultType").value("BATTERY_CREATED"))
                .andReturnJson();
        Assertions.assertThat(noOriginalCode.get("data").get("battery").get("originalCode").isNull()).isTrue();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JsonNode>> noCodeResponses = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int index = i;
            noCodeResponses.add(executor.submit(concurrentCreateBatteryWithoutOriginalCode(token, "i2-no-code-concurrent-" + index, ready, start)));
        }
        Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<String> traceCodes = new ArrayList<>();
        for (Future<JsonNode> response : noCodeResponses) {
            JsonNode created = response.get(20, TimeUnit.SECONDS);
            traceCodes.add(created.get("data").get("battery").get("systemTraceCode").asText());
            Assertions.assertThat(created.get("data").get("battery").get("originalCode").isNull()).isTrue();
        }
        executor.shutdownNow();
        Assertions.assertThat(traceCodes).doesNotHaveDuplicates();

        mockMvc.perform(post("/api/v1/batteries")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-battery-missing-chemistry")
                        .contentType("application/json")
                        .content("""
                                {"batteryType":"PACK"}
                                """))
                .andExpect(status().isBadRequest());

        String original100 = "O".repeat(100);
        createBattery(token, "i2-original-100", original100);
        mockMvc.perform(post("/api/v1/batteries")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-original-101")
                        .contentType("application/json")
                        .content("""
                                {"originalCode":"%s","batteryType":"PACK","batteryChemistry":"UNKNOWN"}
                                """.formatted("O".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/recycle-batches")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-source-name-101")
                        .contentType("application/json")
                        .content("""
                                {"sourceType":"ENTERPRISE","sourceSubjectName":"%s","handoverDate":"2026-09-29"}
                                """.formatted("企".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(11)
    void concurrentRegistrationWithSameOriginalCodeCreatesOneBatteryAndOneCandidate() throws Exception {
        String token = recycleToken();
        String originalCode = "ORI-I2-CONCURRENT-" + System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<JsonNode> first = concurrentCreateBattery(token, "i2-concurrent-original-a", originalCode, ready, start);
        Callable<JsonNode> second = concurrentCreateBattery(token, "i2-concurrent-original-b", originalCode, ready, start);
        Future<JsonNode> firstResult = executor.submit(first);
        Future<JsonNode> secondResult = executor.submit(second);
        Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<String> resultTypes = List.of(
                firstResult.get(20, TimeUnit.SECONDS).get("data").get("resultType").asText(),
                secondResult.get(20, TimeUnit.SECONDS).get("data").get("resultType").asText());
        executor.shutdownNow();

        Assertions.assertThat(resultTypes).containsExactlyInAnyOrder("BATTERY_CREATED", "DUPLICATE_REVIEW_REQUIRED");
        Assertions.assertThat(batteryCountByOriginal(originalCode)).isOne();
        Assertions.assertThat(pendingCandidateCount(originalCode)).isOne();
    }

    @Test
    @Order(12)
    void concurrentDuplicateResolutionOnlySucceedsOnce() throws Exception {
        String token = recycleToken();
        String originalCode = "ORI-I2-RESOLVE-" + System.nanoTime();
        long existingBatteryId = createBattery(token, "i2-resolve-existing", originalCode).get("data").get("battery").get("id").asLong();
        long candidateId = createBattery(token, "i2-resolve-candidate", originalCode).get("data").get("candidateId").asLong();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> first = executor.submit(concurrentResolveDuplicate(token, "i2-resolve-key-a", candidateId, existingBatteryId, ready, start));
        Future<Integer> second = executor.submit(concurrentResolveDuplicate(token, "i2-resolve-key-b", candidateId, existingBatteryId, ready, start));
        Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        Assertions.assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        executor.shutdownNow();
        Assertions.assertThat(duplicateReviewCount(candidateId)).isOne();
        Assertions.assertThat(candidateStatus(candidateId)).isEqualTo("CLOSED_SAME");
    }

    @Test
    @Order(13)
    void unresolvedDuplicateBlocksBatchAddAndSubmitWithStateUnchanged() throws Exception {
        String token = recycleToken();
        String originalCode = "ORI-I2-UNRESOLVED-" + System.nanoTime();
        long batteryId = createBattery(token, "i2-unresolved-battery", originalCode).get("data").get("battery").get("id").asLong();
        createBattery(token, "i2-unresolved-candidate", originalCode);

        long blockedAddBatchId = createBatch(token, "i2-unresolved-add-batch", "未核实加入来源");
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/batteries", blockedAddBatchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-unresolved-add")
                        .contentType("application/json")
                        .content("""
                                {"batteryId":%d}
                                """.formatted(batteryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATTERY_DUPLICATE_UNRESOLVED"));
        Assertions.assertThat(batchStatus(blockedAddBatchId)).isEqualTo("DRAFT");
        Assertions.assertThat(activeBatchRelationCount(batteryId)).isZero();

        String secondOriginalCode = "ORI-I2-UNRESOLVED-SUBMIT-" + System.nanoTime();
        long submitBatteryId = createBattery(token, "i2-unresolved-submit-battery", secondOriginalCode).get("data").get("battery").get("id").asLong();
        long submitBatchId = createBatch(token, "i2-unresolved-submit-batch", "未核实提交来源");
        addBattery(token, submitBatchId, submitBatteryId, "i2-unresolved-submit-add");
        createBattery(token, "i2-unresolved-submit-candidate", secondOriginalCode);
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", submitBatchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-unresolved-submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATTERY_DUPLICATE_UNRESOLVED"));
        Assertions.assertThat(batchStatus(submitBatchId)).isEqualTo("DRAFT");
        Assertions.assertThat(batteryStatus(submitBatteryId)).isEqualTo("REGISTERED");
    }

    @Test
    @Order(14)
    void invalidBatchRelationAndMultiBatterySubmitRollbackAreEnforced() throws Exception {
        String token = recycleToken();
        long submittedBatchId = createBatch(token, "i2-nondraft-add-batch", "非草稿来源");
        long submittedBatteryId = createBattery(token, "i2-nondraft-add-battery", "ORI-I2-NONDRAFT").get("data").get("battery").get("id").asLong();
        addBattery(token, submittedBatchId, submittedBatteryId, "i2-nondraft-add-initial");
        submitBatch(token, submittedBatchId, "i2-nondraft-submit");
        long anotherBatteryId = createBattery(token, "i2-nondraft-another", "ORI-I2-NONDRAFT-OTHER").get("data").get("battery").get("id").asLong();
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/batteries", submittedBatchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-nondraft-add-rejected")
                        .contentType("application/json")
                        .content("""
                                {"batteryId":%d}
                                """.formatted(anotherBatteryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_BATCH_STATE"));

        long invalidStateBatchId = createBatch(token, "i2-invalid-state-add-batch", "状态异常来源");
        long invalidStateBatteryId = createBattery(token, "i2-invalid-state-add-battery", "ORI-I2-BADSTATE").get("data").get("battery").get("id").asLong();
        jdbcTemplate.update("UPDATE battery SET lifecycle_status = 'PENDING_ACCEPTANCE' WHERE id = ?", invalidStateBatteryId);
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/batteries", invalidStateBatchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-invalid-state-add")
                        .contentType("application/json")
                        .content("""
                                {"batteryId":%d}
                                """.formatted(invalidStateBatteryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_BATTERY_STATE"));

        long rollbackBatchId = createBatch(token, "i2-rollback-batch", "回滚来源");
        long firstBatteryId = createBattery(token, "i2-rollback-battery-a", "ORI-I2-ROLL-A").get("data").get("battery").get("id").asLong();
        long secondBatteryId = createBattery(token, "i2-rollback-battery-b", "ORI-I2-ROLL-B").get("data").get("battery").get("id").asLong();
        addBattery(token, rollbackBatchId, firstBatteryId, "i2-rollback-add-a");
        addBattery(token, rollbackBatchId, secondBatteryId, "i2-rollback-add-b");
        jdbcTemplate.update("UPDATE battery SET lifecycle_status = 'PENDING_ACCEPTANCE' WHERE id = ?", secondBatteryId);
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", rollbackBatchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-rollback-submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_BATTERY_STATE"));
        Assertions.assertThat(batchStatus(rollbackBatchId)).isEqualTo("DRAFT");
        Assertions.assertThat(batteryStatus(firstBatteryId)).isEqualTo("REGISTERED");
        Assertions.assertThat(batteryStatus(secondBatteryId)).isEqualTo("PENDING_ACCEPTANCE");
        Assertions.assertThat(batchSubmitEventCount(firstBatteryId, rollbackBatchId)).isZero();
    }

    @Test
    @Order(15)
    void concurrentSubmitOnlyExecutesOnce() throws Exception {
        String token = recycleToken();
        long batchId = createBatch(token, "i2-concurrent-submit-batch", "并发提交来源");
        long batteryId = createBattery(token, "i2-concurrent-submit-battery", "ORI-I2-CONCURRENT-SUBMIT").get("data").get("battery").get("id").asLong();
        addBattery(token, batchId, batteryId, "i2-concurrent-submit-add");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> first = executor.submit(concurrentSubmit(token, "i2-concurrent-submit-a", batchId, ready, start));
        Future<Integer> second = executor.submit(concurrentSubmit(token, "i2-concurrent-submit-b", batchId, ready, start));
        Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        Assertions.assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        executor.shutdownNow();
        Assertions.assertThat(batchStatus(batchId)).isEqualTo("PENDING_ACCEPTANCE");
        Assertions.assertThat(batteryStatus(batteryId)).isEqualTo("PENDING_ACCEPTANCE");
        Assertions.assertThat(batchSubmitEventCount(batteryId, batchId)).isOne();
    }

    @Test
    @Order(16)
    void apiErrorStatusCodesMatchContract() throws Exception {
        String token = recycleToken();
        long emptyBatchId = createBatch(token, "i2-contract-empty-batch", "状态码来源");
        String originalCode = "ORI-I2-CONTRACT-" + System.nanoTime();
        createBattery(token, "i2-contract-existing", originalCode);
        long candidateId = createBattery(token, "i2-contract-candidate", originalCode).get("data").get("candidateId").asLong();

        mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", emptyBatchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-contract-submit-empty"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/batteries/duplicate-check")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"originalCode":""}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/battery-registration-candidates/{id}/duplicate-resolution", candidateId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-contract-resolution-bad")
                        .contentType("application/json")
                        .content("""
                                {"reviewResult":"DIFFERENT_BATTERY"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/batteries", emptyBatchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i2-contract-add-missing")
                        .contentType("application/json")
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/batteries/{id}/trace", 999999999999L)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(17)
    void concurrentSubmitWithSameIdempotencyKeyOnlyExecutesOnce() throws Exception {
        String token = recycleToken();
        long batchId = createBatch(token, "i2-same-key-submit-batch", "同键并发提交来源");
        long batteryId = createBattery(token, "i2-same-key-submit-battery", "ORI-I2-SAME-KEY-SUBMIT").get("data").get("battery").get("id").asLong();
        addBattery(token, batchId, batteryId, "i2-same-key-submit-add");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> first = executor.submit(concurrentSubmit(token, "i2-same-key-submit", batchId, ready, start));
        Future<Integer> second = executor.submit(concurrentSubmit(token, "i2-same-key-submit", batchId, ready, start));
        Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Integer> statuses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        executor.shutdownNow();
        Assertions.assertThat(statuses).contains(200);
        Assertions.assertThat(statuses).allMatch(status -> status == 200 || status == 409);
        Assertions.assertThat(batchSubmitEventCount(batteryId, batchId)).isOne();
        Assertions.assertThat(idempotencyRecordCount("SUBMIT_RECYCLE_BATCH", "i2-same-key-submit")).isOne();
        Assertions.assertThat(batchStatus(batchId)).isEqualTo("PENDING_ACCEPTANCE");
        Assertions.assertThat(batteryStatus(batteryId)).isEqualTo("PENDING_ACCEPTANCE");
    }

    private JsonResult postJson(String path, String token, String key, String body) throws Exception {
        return new JsonResult(mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body)));
    }

    private long createBatch(String token, String key, String sourceName) throws Exception {
        return createBatchNode(token, key, sourceName)
                .get("data").get("id").asLong();
    }

    private JsonNode createBatchNode(String token, String key, String sourceName) throws Exception {
        return postJson("/api/v1/recycle-batches", token, key, """
                {"sourceType":"ENTERPRISE","sourceSubjectName":"%s","handoverDate":"2026-09-29"}
                """.formatted(sourceName))
                .andExpect(status().isOk())
                .andReturnJson();
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

    private Callable<JsonNode> concurrentCreateBattery(String token, String key, String originalCode, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return createBattery(token, key, originalCode);
        };
    }

    private Callable<JsonNode> concurrentCreateBatteryWithoutOriginalCode(String token, String key, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return postJson("/api/v1/batteries", token, key, """
                    {"batteryType":"PACK","batteryChemistry":"UNKNOWN"}
                    """)
                    .andExpect(status().isOk())
                    .andReturnJson();
        };
    }

    private Callable<Integer> concurrentResolveDuplicate(String token, String key, long candidateId, long existingBatteryId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            MvcResult result = mockMvc.perform(post("/api/v1/battery-registration-candidates/{id}/duplicate-resolution", candidateId)
                            .header("Authorization", bearer(token))
                            .header("Idempotency-Key", key)
                            .contentType("application/json")
                            .content("""
                                    {"reviewResult":"SAME_BATTERY","existingBatteryId":%d}
                                    """.formatted(existingBatteryId)))
                    .andReturn();
            return result.getResponse().getStatus();
        };
    }

    private Callable<Integer> concurrentAddBattery(String token, String key, long batchId, long batteryId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            MvcResult result = mockMvc.perform(post("/api/v1/recycle-batches/{id}/batteries", batchId)
                            .header("Authorization", bearer(token))
                            .header("Idempotency-Key", key)
                            .contentType("application/json")
                            .content("""
                                    {"batteryId":%d}
                                    """.formatted(batteryId)))
                    .andReturn();
            return result.getResponse().getStatus();
        };
    }

    private Callable<Integer> concurrentSubmit(String token, String key, long batchId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            MvcResult result = mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", batchId)
                            .header("Authorization", bearer(token))
                            .header("Idempotency-Key", key))
                    .andReturn();
            return result.getResponse().getStatus();
        };
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

    private long insertForeignBattery(String originalCode) {
        long id = nextManualId();
        jdbcTemplate.update("""
                INSERT INTO battery (
                  id, enterprise_id, system_trace_code, original_code, battery_type, battery_chemistry,
                  current_responsible_enterprise_id, lifecycle_status, duplicate_status,
                  created_by, created_at, updated_at, version
                )
                VALUES (?, 2, ?, ?, 'PACK', 'UNKNOWN', 2, 'REGISTERED', 'NORMAL', 6, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, id, "BAT-" + id, originalCode);
        return id;
    }

    private long insertCandidate(Long enterpriseId, String originalCode) {
        long id = nextManualId();
        long submittedBy = enterpriseId == 1L ? 3L : 6L;
        jdbcTemplate.update("""
                INSERT INTO battery_registration_candidate (
                  id, enterprise_id, original_code, battery_type, battery_chemistry, candidate_status,
                  submitted_by, submitted_at, created_at, updated_at, version
                )
                VALUES (?, ?, ?, 'PACK', 'UNKNOWN', 'PENDING_REVIEW', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, id, enterpriseId, originalCode, submittedBy);
        return id;
    }

    private long nextManualId() {
        return manualId++;
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

    private int totalBatteryCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM battery", Integer.class);
    }

    private int totalCandidateCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM battery_registration_candidate", Integer.class);
    }

    private int totalDuplicateReviewCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM duplicate_code_review", Integer.class);
    }

    private int activeBatchRelationCount(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recycle_batch_battery WHERE battery_id = ? AND relation_status = 'ACTIVE'", Integer.class, batteryId);
    }

    private int batteryCountByOriginal(String originalCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM battery WHERE enterprise_id = 1 AND original_code = ?", Integer.class, originalCode);
    }

    private int pendingCandidateCount(String originalCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM battery_registration_candidate WHERE enterprise_id = 1 AND original_code = ? AND candidate_status = 'PENDING_REVIEW'", Integer.class, originalCode);
    }

    private int duplicateReviewCount(long candidateId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM duplicate_code_review WHERE candidate_id = ?", Integer.class, candidateId);
    }

    private String candidateStatus(long candidateId) {
        return jdbcTemplate.queryForObject("SELECT candidate_status FROM battery_registration_candidate WHERE id = ?", String.class, candidateId);
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

    private int idempotencyRecordCount(String operationCode, String idempotencyKey) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE operation_code = ? AND idempotency_key = ?", Integer.class, operationCode, idempotencyKey);
    }

    private int forbiddenAuditCount(String actionCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND result = 'FORBIDDEN'", Integer.class, actionCode);
    }

    private int successAuditCountByDetail(String actionCode, String detail) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND result = 'SUCCESS' AND reject_reason = ?", Integer.class, actionCode, detail);
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
