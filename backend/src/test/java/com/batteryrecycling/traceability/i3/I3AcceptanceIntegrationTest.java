package com.batteryrecycling.traceability.i3;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class I3AcceptanceIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void acceptanceResultsSupplementLoopAndBatchProgressAreClosed() throws Exception {
        String token = recycleToken();
        long batchId = createSubmittedBatch(token, "i3-flow", List.of("ORI-I3-FLOW-A", "ORI-I3-FLOW-B", "ORI-I3-FLOW-C"));
        List<Long> batteryIds = activeBatteryIds(batchId);

        String pendingResponse = mockMvc.perform(get("/api/v1/acceptances/pending").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assertions.assertThat(objectMapper.readTree(pendingResponse).path("data").findValues("id").stream()
                        .map(JsonNode::asLong)
                        .toList())
                .contains(batteryIds.get(0));

        createAcceptance(token, batteryIds.get(0), "i3-pass-a", "PASS", "身份一致", "外观完整", "资料完整", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batteryStatus").value("ACCEPTED_PENDING_INBOUND"));
        Assertions.assertThat(batteryStatus(batteryIds.get(0))).isEqualTo("ACCEPTED_PENDING_INBOUND");
        Assertions.assertThat(batchStatus(batchId)).isEqualTo("ACCEPTANCE_PROCESSING");

        createAcceptance(token, batteryIds.get(1), "i3-need-b", "NEED_SUPPLEMENT", "身份一致", "外观需说明", "资料缺失", "缺少来源照片")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batteryStatus").value("PENDING_SUPPLEMENT"));
        Assertions.assertThat(batteryStatus(batteryIds.get(1))).isEqualTo("PENDING_SUPPLEMENT");

        supplement(token, batteryIds.get(1), "i3-supp-b", """
                {"supplementNote":"补充来源照片说明"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PENDING_ACCEPTANCE"));

        createAcceptance(token, batteryIds.get(1), "i3-reject-b", "REJECT", "身份一致", "外观破损", "资料完整", "外观严重破损")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batteryStatus").value("ACCEPTANCE_REJECTED"));
        createAcceptance(token, batteryIds.get(2), "i3-pass-c", "PASS", "身份一致", "外观完整", "资料完整", null)
                .andExpect(status().isOk());

        Assertions.assertThat(batchStatus(batchId)).isEqualTo("COMPLETED");
        Assertions.assertThat(acceptanceCount(batteryIds.get(1))).isEqualTo(2);
        mockMvc.perform(get("/api/v1/batteries/{id}/trace", batteryIds.get(1)).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventName", hasItem("验收待补充资料")))
                .andExpect(jsonPath("$.data[*].eventName", hasItem("验收资料已补充")))
                .andExpect(jsonPath("$.data[*].eventName", hasItem("验收不通过")));
    }

    @Test
    void validationFailuresKeepBatteryBatchAndHistoryUnchanged() throws Exception {
        String token = recycleToken();
        long batchId = createSubmittedBatch(token, "i3-validation", List.of("ORI-I3-VAL"));
        long batteryId = activeBatteryIds(batchId).get(0);
        int recordsBefore = acceptanceCount(batteryId);

        createAcceptance(token, batteryId, "i3-validation-missing", "PASS", "", "外观完整", "资料完整", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCEPTANCE_REQUIRED_FIELD_MISSING"));
        createAcceptance(token, batteryId, "i3-validation-note", "NEED_SUPPLEMENT", "身份一致", "外观完整", "资料缺失", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCEPTANCE_REQUIRED_FIELD_MISSING"));
        createAcceptance(token, batteryId, "i3-validation-length", "PASS", "A".repeat(41), "外观完整", "资料完整", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCEPTANCE_FIELD_TOO_LONG"));

        Assertions.assertThat(batteryStatus(batteryId)).isEqualTo("PENDING_ACCEPTANCE");
        Assertions.assertThat(batchStatus(batchId)).isEqualTo("PENDING_ACCEPTANCE");
        Assertions.assertThat(acceptanceCount(batteryId)).isEqualTo(recordsBefore);
    }

    @Test
    void supplementWithAttachmentsIsAtomicAndRejectsInvalidAttachments() throws Exception {
        String token = recycleToken();
        long batchId = createSubmittedBatch(token, "i3-attachment", List.of("ORI-I3-ATT"));
        long batteryId = activeBatteryIds(batchId).get(0);
        createAcceptance(token, batteryId, "i3-attachment-need", "NEED_SUPPLEMENT", "身份一致", "外观完整", "资料缺失", "缺附件")
                .andExpect(status().isOk());
        long attachmentId = uploadAttachment(token, "i3-proof.txt");

        supplement(token, batteryId, "i3-attachment-only", """
                {"attachmentIds":[%d]}
                """.formatted(attachmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PENDING_ACCEPTANCE"));
        Assertions.assertThat(attachmentStatus(attachmentId)).isEqualTo("BOUND");
        Assertions.assertThat(attachmentObjectType(attachmentId)).isEqualTo("ACCEPTANCE_SUPPLEMENT");
        Assertions.assertThat(supplementNote(attachmentId)).isEqualTo("");

        createAcceptance(token, batteryId, "i3-attachment-need-again", "NEED_SUPPLEMENT", "身份一致", "外观完整", "资料仍缺", "再次补充")
                .andExpect(status().isOk());
        supplement(token, batteryId, "i3-attachment-bound-again", """
                {"attachmentIds":[%d]}
                """.formatted(attachmentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTACHMENT"));
        Assertions.assertThat(batteryStatus(batteryId)).isEqualTo("PENDING_SUPPLEMENT");
    }

    @Test
    void permissionsAndEnterpriseIsolationAreEnforced() throws Exception {
        String recycleToken = recycleToken();
        String warehouseToken = tokenFor("warehouse_admin", "password");
        ensureEnterpriseBRecycleUser();
        String enterpriseBToken = tokenFor("i3_enterprise_b_recycle", "password");
        long batchId = createSubmittedBatch(recycleToken, "i3-permission", List.of("ORI-I3-PERM"));
        long batteryId = activeBatteryIds(batchId).get(0);

        createAcceptance(warehouseToken, batteryId, "i3-warehouse-denied", "PASS", "身份一致", "外观完整", "资料完整", null)
                .andExpect(status().isForbidden());
        createAcceptance(enterpriseBToken, batteryId, "i3-cross-denied", "PASS", "身份一致", "外观完整", "资料完整", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
        Assertions.assertThat(batteryStatus(batteryId)).isEqualTo("PENDING_ACCEPTANCE");
    }

    @Test
    void idempotencyAndConcurrentAcceptanceAllowOnlyOneStateTransition() throws Exception {
        String token = recycleToken();
        long batchId = createSubmittedBatch(token, "i3-idempotency", List.of("ORI-I3-IDEMP", "ORI-I3-CONCURRENT"));
        List<Long> batteryIds = activeBatteryIds(batchId);

        String first = createAcceptance(token, batteryIds.get(0), "i3-same-key", "PASS", "身份一致", "外观完整", "资料完整", null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String retry = createAcceptance(token, batteryIds.get(0), "i3-same-key", "PASS", "身份一致", "外观完整", "资料完整", null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assertions.assertThat(objectMapper.readTree(retry).get("data").get("acceptanceRecordId").asLong())
                .isEqualTo(objectMapper.readTree(first).get("data").get("acceptanceRecordId").asLong());
        createAcceptance(token, batteryIds.get(0), "i3-same-key", "REJECT", "身份一致", "外观破损", "资料完整", "不通过")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> pass = executor.submit(concurrentAcceptance(token, "i3-concurrent-pass", batteryIds.get(1), "PASS", ready, start));
            Future<Integer> reject = executor.submit(concurrentAcceptance(token, "i3-concurrent-reject", batteryIds.get(1), "REJECT", ready, start));
            Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(pass.get(20, TimeUnit.SECONDS), reject.get(20, TimeUnit.SECONDS)));
            Collections.sort(statuses);
            Assertions.assertThat(statuses).containsExactly(200, 409);
        } finally {
            executor.shutdownNow();
        }
        Assertions.assertThat(successAuditCount("ACCEPTANCE_CREATED", batteryIds.get(1))).isOne();
        Assertions.assertThat(acceptanceCount(batteryIds.get(1))).isOne();
    }

    @Test
    void concurrentFinalMembersCompleteBatchAndDeleteEffectiveAcceptanceIsProtected() throws Exception {
        String token = recycleToken();
        long batchId = createSubmittedBatch(token, "i3-batch-complete", List.of("ORI-I3-COMP-A", "ORI-I3-COMP-B"));
        List<Long> batteryIds = activeBatteryIds(batchId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(concurrentAcceptance(token, "i3-complete-a", batteryIds.get(0), "PASS", ready, start));
            Future<Integer> second = executor.submit(concurrentAcceptance(token, "i3-complete-b", batteryIds.get(1), "PASS", ready, start));
            Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Assertions.assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            Assertions.assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }
        Assertions.assertThat(batchStatus(batchId)).isEqualTo("COMPLETED");
        Long acceptanceId = latestAcceptanceId(batteryIds.get(0));
        mockMvc.perform(delete("/api/v1/acceptance-records/{id}", acceptanceId).header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EFFECTIVE_RECORD_DELETE_FORBIDDEN"));
        Assertions.assertThat(forbiddenAuditCount("ACCEPTANCE_RECORD_DELETE_FORBIDDEN")).isGreaterThanOrEqualTo(1);
    }

    private long createSubmittedBatch(String token, String prefix, List<String> originalCodes) throws Exception {
        long batchId = postJson("/api/v1/recycle-batches", token, prefix + "-batch", """
                {"sourceType":"ENTERPRISE","sourceSubjectName":"%s来源","handoverDate":"2026-10-07"}
                """.formatted(prefix))
                .andExpect(status().isOk())
                .andReturnJson().get("data").get("id").asLong();
        int index = 0;
        for (String originalCode : originalCodes) {
            long batteryId = postJson("/api/v1/batteries", token, prefix + "-battery-" + index, """
                    {"originalCode":"%s","batteryType":"PACK","batteryChemistry":"UNKNOWN"}
                    """.formatted(originalCode))
                    .andExpect(status().isOk())
                    .andReturnJson().get("data").get("battery").get("id").asLong();
            mockMvc.perform(post("/api/v1/recycle-batches/{id}/batteries", batchId)
                            .header("Authorization", bearer(token))
                            .header("Idempotency-Key", prefix + "-add-" + index)
                            .contentType("application/json")
                            .content("""
                                    {"batteryId":%d}
                                    """.formatted(batteryId)))
                    .andExpect(status().isOk());
            index++;
        }
        mockMvc.perform(post("/api/v1/recycle-batches/{id}/submit", batchId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", prefix + "-submit"))
                .andExpect(status().isOk());
        return batchId;
    }

    private JsonResult createAcceptance(String token, long batteryId, String key, String result, String identity, String appearance, String document, String note) throws Exception {
        String notePart = note == null ? "" : ", \"acceptanceNote\":\"%s\"".formatted(note);
        return new JsonResult(mockMvc.perform(post("/api/v1/batteries/{id}/acceptances", batteryId)
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("""
                        {"acceptanceResult":"%s","identityCheckResult":"%s","appearanceCheckResult":"%s","documentCheckResult":"%s"%s}
                        """.formatted(result, identity, appearance, document, notePart))));
    }

    private JsonResult supplement(String token, long batteryId, String key, String body) throws Exception {
        return new JsonResult(mockMvc.perform(post("/api/v1/batteries/{id}/acceptance-supplements", batteryId)
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body)));
    }

    private Callable<Integer> concurrentAcceptance(String token, String key, long batteryId, String result, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            MvcResult mvcResult = createAcceptance(token, batteryId, key, result, "身份一致", "PASS".equals(result) ? "外观完整" : "外观破损", "资料完整", "PASS".equals(result) ? null : "不通过").andReturn();
            return mvcResult.getResponse().getStatus();
        };
    }

    private long uploadAttachment(String token, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", "proof".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(file)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "i3-upload-" + fileName))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    private String recycleToken() throws Exception {
        return tokenFor("recycle_operator", "password");
    }

    private String tokenFor(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("accessToken").asText();
    }

    private JsonResult postJson(String path, String token, String key, String body) throws Exception {
        return new JsonResult(mockMvc.perform(post(path)
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body)));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private List<Long> activeBatteryIds(long batchId) {
        return jdbcTemplate.queryForList("""
                SELECT battery_id
                FROM recycle_batch_battery
                WHERE batch_id = ? AND relation_status = 'ACTIVE'
                ORDER BY created_at, id
                """, Long.class, batchId);
    }

    private String batteryStatus(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT lifecycle_status FROM battery WHERE id = ?", String.class, batteryId);
    }

    private String batchStatus(long batchId) {
        return jdbcTemplate.queryForObject("SELECT batch_status FROM recycle_batch WHERE id = ?", String.class, batchId);
    }

    private int acceptanceCount(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM acceptance_record WHERE battery_id = ?", Integer.class, batteryId);
    }

    private Long latestAcceptanceId(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT id FROM acceptance_record WHERE battery_id = ? ORDER BY accepted_at DESC, id DESC LIMIT 1", Long.class, batteryId);
    }

    private String attachmentStatus(long attachmentId) {
        return jdbcTemplate.queryForObject("SELECT binding_status FROM business_attachment WHERE id = ?", String.class, attachmentId);
    }

    private String attachmentObjectType(long attachmentId) {
        return jdbcTemplate.queryForObject("SELECT object_type FROM business_attachment WHERE id = ?", String.class, attachmentId);
    }

    private String supplementNote(long attachmentId) {
        return jdbcTemplate.queryForObject("""
                SELECT s.supplement_note
                FROM acceptance_supplement s
                JOIN business_attachment a ON a.object_id = s.id
                WHERE a.id = ?
                """, String.class, attachmentId);
    }

    private int successAuditCount(String actionCode, long batteryId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND result = 'SUCCESS' AND reject_reason LIKE ?", Integer.class, actionCode, "%batteryId=" + batteryId + "%");
    }

    private int forbiddenAuditCount(String actionCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND result = 'FORBIDDEN'", Integer.class, actionCode);
    }

    private void ensureEnterpriseBRecycleUser() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, enterprise_id, username, password_hash, display_name, enabled_status, created_at, updated_at, version)
                VALUES (880000300000000002, 2, 'i3_enterprise_b_recycle', '$2a$10$e.zfYCvFFe6RsksxE2IxmuV/t79vateuo4hsQ7072lvKHSUjqrMrC', 'I3企业B回收操作员', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                ON DUPLICATE KEY UPDATE enabled_status = 'ENABLED', updated_at = CURRENT_TIMESTAMP(3)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (id, user_id, role_id, created_at)
                SELECT 880000300000000003, 880000300000000002, r.id, CURRENT_TIMESTAMP(3)
                FROM sys_role r
                WHERE r.role_code = 'RECYCLE_OPERATOR'
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = 880000300000000002 AND ur.role_id = r.id
                  )
                """);
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
            return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }
}
