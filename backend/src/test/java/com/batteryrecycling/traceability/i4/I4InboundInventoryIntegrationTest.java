package com.batteryrecycling.traceability.i4;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class I4InboundInventoryIntegrationTest {
    private static final long ENABLED_WAREHOUSE_ID = 20101L;
    private static final long DISABLED_WAREHOUSE_ID = 20102L;
    private static final long ENTERPRISE_B_WAREHOUSE_ID = 20201L;
    private static final long ENABLED_LOCATION_ID = 21101L;
    private static final long DISABLED_LOCATION_ID = 21102L;
    private static final long MISMATCH_LOCATION_ID = 21103L;
    private static final long ENTERPRISE_B_LOCATION_ID = 21201L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    IdGenerator idGenerator;

    @Test
    void inboundCreatesEffectiveRecordCurrentInventoryTraceAndInventoryView() throws Exception {
        String recycleToken = tokenFor("recycle_operator", "password");
        String warehouseToken = tokenFor("warehouse_admin", "password");
        String supervisorToken = tokenFor("supervisor", "password");
        long batchId = createSubmittedBatch(recycleToken, "i4-flow", List.of("ORI-I4-FLOW"));
        long batteryId = activeBatteryIds(batchId).get(0);
        createAcceptance(recycleToken, batteryId, "i4-flow-pass", "PASS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batteryStatus").value("ACCEPTED_PENDING_INBOUND"));

        mockMvc.perform(get("/api/v1/inbounds/pending").header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(String.valueOf(batteryId))));
        mockMvc.perform(get("/api/v1/warehouses").header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(String.valueOf(ENABLED_WAREHOUSE_ID))))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(String.valueOf(DISABLED_WAREHOUSE_ID)))));
        mockMvc.perform(get("/api/v1/warehouses/{id}/locations", ENABLED_WAREHOUSE_ID).header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(String.valueOf(ENABLED_LOCATION_ID))))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(String.valueOf(DISABLED_LOCATION_ID)))));

        JsonNode inbound = createInbound(warehouseToken, batteryId, "i4-flow-inbound", ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batteryStatus").value("IN_STOCK"))
                .andReturnJson().get("data");
        long inboundRecordId = inbound.get("inboundRecordId").asLong();
        long inventoryId = inbound.get("inventoryId").asLong();

        Assertions.assertThat(batteryStatus(batteryId)).isEqualTo("IN_STOCK");
        Assertions.assertThat(batchStatus(batchId)).isEqualTo("COMPLETED");
        Assertions.assertThat(inboundCount(batteryId)).isEqualTo(1);
        Assertions.assertThat(currentInventoryCount(batteryId)).isEqualTo(1);
        Assertions.assertThat(inboundAt(inboundRecordId)).isAfterOrEqualTo(acceptedAt(batteryId));
        Assertions.assertThat(inventoryId).isEqualTo(latestInventoryId(batteryId));

        String traceResponse = mockMvc.perform(get("/api/v1/batteries/{id}/trace", batteryId).header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventName", hasItem("入库完成")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode inboundEvent = event(objectMapper.readTree(traceResponse).get("data"), "入库完成");
        Assertions.assertThat(inboundEvent.at("/details/inbound/inboundNo").asText()).isEqualTo("IB-" + inboundRecordId);
        Assertions.assertThat(inboundEvent.at("/details/inbound/locationCode").asText()).isEqualTo("WH-101-A01-R01-L01");

        String traceCode = systemTraceCode(batteryId);
        mockMvc.perform(get("/api/v1/inventory").param("systemTraceCode", traceCode).header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(String.valueOf(inventoryId))));
        mockMvc.perform(get("/api/v1/inventory").param("systemTraceCode", traceCode).header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(String.valueOf(inventoryId))));
        mockMvc.perform(get("/api/v1/inventory").header("Authorization", bearer(recycleToken)))
                .andExpect(status().isForbidden());

        int deleteAuditBefore = failedAuditCount("INBOUND_RECORD_DELETE_FORBIDDEN", inboundRecordId);
        mockMvc.perform(delete("/api/v1/inbound-records/{id}", inboundRecordId).header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EFFECTIVE_RECORD_DELETE_FORBIDDEN"));
        Assertions.assertThat(failedAuditCount("INBOUND_RECORD_DELETE_FORBIDDEN", inboundRecordId)).isGreaterThan(deleteAuditBefore);
    }

    @Test
    void validationWarehouseLocationAndInvalidStatesAreRejectedAtomically() throws Exception {
        String recycleToken = tokenFor("recycle_operator", "password");
        String warehouseToken = tokenFor("warehouse_admin", "password");
        long batchId = createSubmittedBatch(recycleToken, "i4-invalid-state", List.of("ORI-I4-PENDING", "ORI-I4-SUPPLEMENT", "ORI-I4-REJECT", "ORI-I4-STOCK"));
        List<Long> batteries = activeBatteryIds(batchId);
        createAcceptance(recycleToken, batteries.get(1), "i4-invalid-need", "NEED_SUPPLEMENT")
                .andExpect(status().isOk());
        createAcceptance(recycleToken, batteries.get(2), "i4-invalid-reject", "REJECT")
                .andExpect(status().isOk());
        createAcceptance(recycleToken, batteries.get(3), "i4-invalid-pass", "PASS")
                .andExpect(status().isOk());
        createInbound(warehouseToken, batteries.get(3), "i4-invalid-stock", ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                .andExpect(status().isOk());

        for (long batteryId : batteries) {
            createInbound(warehouseToken, batteryId, "i4-invalid-state-" + batteryId, ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_BATTERY_STATE"));
        }

        long accepted = acceptedBatteryDirect(1L, 3L, "ORI-I4-WAREHOUSE-RULES");
        int inboundBefore = inboundCount(accepted);
        String statusBefore = batteryStatus(accepted);
        postRawInbound(warehouseToken, accepted, "i4-validation-empty-warehouse", """
                {"locationId":%d}
                """.formatted(ENABLED_LOCATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        createInbound(warehouseToken, accepted, "i4-validation-disabled-warehouse", DISABLED_WAREHOUSE_ID, MISMATCH_LOCATION_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_DISABLED"));
        createInbound(warehouseToken, accepted, "i4-validation-disabled-location", ENABLED_WAREHOUSE_ID, DISABLED_LOCATION_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCATION_DISABLED"));
        createInbound(warehouseToken, accepted, "i4-validation-mismatch", ENABLED_WAREHOUSE_ID, MISMATCH_LOCATION_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCATION_WAREHOUSE_MISMATCH"));

        Assertions.assertThat(batteryStatus(accepted)).isEqualTo(statusBefore);
        Assertions.assertThat(inboundCount(accepted)).isEqualTo(inboundBefore);
        Assertions.assertThat(currentInventoryCount(accepted)).isZero();
        Assertions.assertThat(failedAuditCount("INBOUND_VALIDATION_FAILED", accepted)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void idempotencyAndConcurrentInboundAllowOnlyOneEffectiveInventory() throws Exception {
        String warehouseToken = tokenFor("warehouse_admin", "password");
        long idempotentBattery = acceptedBatteryDirect(1L, 3L, "ORI-I4-IDEMPOTENT");
        JsonNode first = createInbound(warehouseToken, idempotentBattery, "i4-idempotent-same", ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                .andExpect(status().isOk())
                .andReturnJson().get("data");
        JsonNode second = createInbound(warehouseToken, idempotentBattery, "i4-idempotent-same", ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                .andExpect(status().isOk())
                .andReturnJson().get("data");
        Assertions.assertThat(second.get("inboundRecordId").asText()).isEqualTo(first.get("inboundRecordId").asText());
        Assertions.assertThat(inboundCount(idempotentBattery)).isEqualTo(1);
        createInbound(warehouseToken, idempotentBattery, "i4-idempotent-same", ENABLED_WAREHOUSE_ID, DISABLED_LOCATION_ID)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        long concurrentBattery = acceptedBatteryDirect(1L, 3L, "ORI-I4-CONCURRENT");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstStatus = executor.submit(concurrentInbound(warehouseToken, concurrentBattery, "i4-concurrent-a", ready, start));
            Future<Integer> secondStatus = executor.submit(concurrentInbound(warehouseToken, concurrentBattery, "i4-concurrent-b", ready, start));
            Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Assertions.assertThat(List.of(firstStatus.get(20, TimeUnit.SECONDS), secondStatus.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        Assertions.assertThat(inboundCount(concurrentBattery)).isEqualTo(1);
        Assertions.assertThat(currentInventoryCount(concurrentBattery)).isEqualTo(1);
        Assertions.assertThat(successAuditCount("INBOUND_COMPLETED", "INBOUND_RECORD")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void enterpriseIsolationAndRolePermissionsAreEnforced() throws Exception {
        ensureEnterpriseBWarehouseAdmin();
        String warehouseToken = tokenFor("warehouse_admin", "password");
        String supervisorToken = tokenFor("supervisor", "password");
        String recycleToken = tokenFor("recycle_operator", "password");
        String adminToken = tokenFor("admin", "password");
        long enterpriseBBattery = acceptedBatteryDirect(2L, 880000400000000002L, "ORI-I4-ENTERPRISE-B");
        long enterpriseABatteryForWarehouse = acceptedBatteryDirect(1L, 3L, "ORI-I4-CROSS-WAREHOUSE");
        long enterpriseABatteryForLocation = acceptedBatteryDirect(1L, 3L, "ORI-I4-CROSS-LOCATION");

        createInbound(warehouseToken, enterpriseBBattery, "i4-cross-battery", ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
        createInbound(warehouseToken, enterpriseABatteryForWarehouse, "i4-cross-warehouse", ENTERPRISE_B_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
        createInbound(warehouseToken, enterpriseABatteryForLocation, "i4-cross-location", ENABLED_WAREHOUSE_ID, ENTERPRISE_B_LOCATION_ID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/warehouses/{id}/locations", ENTERPRISE_B_WAREHOUSE_ID).header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ENTERPRISE_ACCESS_DENIED"));

        createInbound(supervisorToken, enterpriseABatteryForWarehouse, "i4-role-supervisor", ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                .andExpect(status().isForbidden());
        createInbound(recycleToken, enterpriseABatteryForWarehouse, "i4-role-recycle", ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                .andExpect(status().isForbidden());
        createInbound(adminToken, enterpriseABatteryForWarehouse, "i4-role-admin", ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID)
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/warehouses").header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/inbounds/pending").header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(String.valueOf(enterpriseBBattery)))));
        Assertions.assertThat(forbiddenAuditCount("BATTERY_CROSS_ENTERPRISE_DENIED")).isGreaterThanOrEqualTo(1);
        Assertions.assertThat(forbiddenAuditCount("WAREHOUSE_CROSS_ENTERPRISE_DENIED")).isGreaterThanOrEqualTo(1);
        Assertions.assertThat(forbiddenAuditCount("LOCATION_CROSS_ENTERPRISE_DENIED")).isGreaterThanOrEqualTo(1);
    }

    private long createSubmittedBatch(String token, String prefix, List<String> originalCodes) throws Exception {
        long batchId = postJson("/api/v1/recycle-batches", token, prefix + "-batch", """
                {"sourceType":"ENTERPRISE","sourceSubjectName":"%s来源","handoverDate":"2026-10-10"}
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

    private JsonResult createAcceptance(String token, long batteryId, String key, String result) throws Exception {
        String notePart = "PASS".equals(result) ? "" : ", \"acceptanceNote\":\"需要说明\"";
        return new JsonResult(mockMvc.perform(post("/api/v1/batteries/{id}/acceptances", batteryId)
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("""
                        {"acceptanceResult":"%s","identityCheckResult":"身份一致","appearanceCheckResult":"外观完整","documentCheckResult":"资料完整"%s}
                        """.formatted(result, notePart))));
    }

    private JsonResult createInbound(String token, long batteryId, String key, long warehouseId, long locationId) throws Exception {
        return postRawInbound(token, batteryId, key, """
                {"warehouseId":%d,"locationId":%d}
                """.formatted(warehouseId, locationId));
    }

    private JsonResult postRawInbound(String token, long batteryId, String key, String body) throws Exception {
        return new JsonResult(mockMvc.perform(post("/api/v1/batteries/{id}/inbounds", batteryId)
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body)));
    }

    private Callable<Integer> concurrentInbound(String token, long batteryId, String key, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            MvcResult result = createInbound(token, batteryId, key, ENABLED_WAREHOUSE_ID, ENABLED_LOCATION_ID).andReturn();
            return result.getResponse().getStatus();
        };
    }

    private long acceptedBatteryDirect(long enterpriseId, long userId, String originalCode) {
        long batchId = idGenerator.nextId();
        long batteryId = idGenerator.nextId();
        long relationId = idGenerator.nextId();
        long acceptanceId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO recycle_batch (
                  id, enterprise_id, batch_no, source_type, source_subject_name, handover_date,
                  batch_status, submitted_at, created_by, created_at, updated_at, version
                )
                VALUES (?, ?, ?, 'ENTERPRISE', 'I4测试来源', ?, 'COMPLETED', CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, batchId, enterpriseId, "RB-I4-" + batchId, LocalDate.of(2026, 10, 10), userId);
        jdbcTemplate.update("""
                INSERT INTO battery (
                  id, enterprise_id, system_trace_code, original_code, battery_type, battery_chemistry,
                  current_responsible_enterprise_id, lifecycle_status, duplicate_status,
                  created_by, created_at, updated_by, updated_at, version
                )
                VALUES (?, ?, ?, ?, 'PACK', 'UNKNOWN', ?, 'ACCEPTED_PENDING_INBOUND', 'NORMAL', ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3), 0)
                """, batteryId, enterpriseId, "BAT-" + batteryId, originalCode, enterpriseId, userId, userId);
        jdbcTemplate.update("""
                INSERT INTO recycle_batch_battery (id, enterprise_id, batch_id, battery_id, relation_status, created_by, created_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP(3))
                """, relationId, enterpriseId, batchId, batteryId, userId);
        jdbcTemplate.update("""
                INSERT INTO acceptance_record (
                  id, enterprise_id, battery_id, batch_id, acceptance_result, identity_check_result,
                  appearance_check_result, document_check_result, accepted_by, accepted_at, created_at, version
                )
                VALUES (?, ?, ?, ?, 'PASS', '身份一致', '外观完整', '资料完整', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """, acceptanceId, enterpriseId, batteryId, batchId, userId);
        return batteryId;
    }

    private void ensureEnterpriseBWarehouseAdmin() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, enterprise_id, username, password_hash, display_name, enabled_status, created_at, updated_at, version)
                VALUES (880000400000000002, 2, 'i4_enterprise_b_warehouse', '$2a$10$e.zfYCvFFe6RsksxE2IxmuV/t79vateuo4hsQ7072lvKHSUjqrMrC', 'I4企业B仓库管理员', 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                ON DUPLICATE KEY UPDATE enabled_status = 'ENABLED', updated_at = CURRENT_TIMESTAMP(3)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (id, user_id, role_id, created_at)
                SELECT 880000400000000003, 880000400000000002, r.id, CURRENT_TIMESTAMP(3)
                FROM sys_role r
                WHERE r.role_code = 'WAREHOUSE_ADMIN'
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = 880000400000000002 AND ur.role_id = r.id
                  )
                """);
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
        return jdbcTemplate.queryForList("SELECT battery_id FROM recycle_batch_battery WHERE batch_id = ? AND relation_status = 'ACTIVE' ORDER BY created_at, id", Long.class, batchId);
    }

    private String batteryStatus(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT lifecycle_status FROM battery WHERE id = ?", String.class, batteryId);
    }

    private String batchStatus(long batchId) {
        return jdbcTemplate.queryForObject("SELECT batch_status FROM recycle_batch WHERE id = ?", String.class, batchId);
    }

    private String systemTraceCode(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT system_trace_code FROM battery WHERE id = ?", String.class, batteryId);
    }

    private int inboundCount(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inbound_record WHERE battery_id = ?", Integer.class, batteryId);
    }

    private int currentInventoryCount(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory WHERE battery_id = ? AND is_current = 1", Integer.class, batteryId);
    }

    private long latestInventoryId(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT id FROM inventory WHERE battery_id = ? AND is_current = 1 ORDER BY id DESC LIMIT 1", Long.class, batteryId);
    }

    private java.time.LocalDateTime acceptedAt(long batteryId) {
        return jdbcTemplate.queryForObject("SELECT MAX(accepted_at) FROM acceptance_record WHERE battery_id = ? AND acceptance_result = 'PASS'", java.time.LocalDateTime.class, batteryId);
    }

    private java.time.LocalDateTime inboundAt(long inboundRecordId) {
        return jdbcTemplate.queryForObject("SELECT inbound_at FROM inbound_record WHERE id = ?", java.time.LocalDateTime.class, inboundRecordId);
    }

    private int failedAuditCount(String actionCode, long objectId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND result = 'FAILED' AND object_id = ?", Integer.class, actionCode, objectId);
    }

    private int successAuditCount(String actionCode, String objectType) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND object_type = ? AND result = 'SUCCESS'", Integer.class, actionCode, objectType);
    }

    private int forbiddenAuditCount(String actionCode) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code = ? AND result = 'FORBIDDEN'", Integer.class, actionCode);
    }

    private JsonNode event(JsonNode events, String eventName) {
        for (JsonNode event : events) {
            if (eventName.equals(event.get("eventName").asText())) {
                return event;
            }
        }
        throw new AssertionError("Trace event not found: " + eventName);
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
