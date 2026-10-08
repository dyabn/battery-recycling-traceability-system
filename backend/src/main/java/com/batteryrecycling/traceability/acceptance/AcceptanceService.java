package com.batteryrecycling.traceability.acceptance;

import com.batteryrecycling.traceability.acceptance.AcceptanceDtos.AcceptanceCreateRequest;
import com.batteryrecycling.traceability.acceptance.AcceptanceDtos.AcceptanceResult;
import com.batteryrecycling.traceability.acceptance.AcceptanceDtos.AcceptanceSupplementRequest;
import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.battery.BatteryDtos.BatteryDto;
import com.batteryrecycling.traceability.battery.BatteryService;
import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.idempotency.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AcceptanceService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final BatteryService batteryService;

    public AcceptanceService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, IdempotencyService idempotencyService, AuditService auditService, BatteryService batteryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.batteryService = batteryService;
    }

    public List<BatteryDto> listPending(CurrentUser currentUser) {
        return jdbcTemplate.query("""
                SELECT b.id, b.enterprise_id, b.system_trace_code, b.original_code, b.battery_type, b.battery_model,
                       b.manufacturer, b.battery_chemistry, b.nominal_capacity, b.production_date,
                       b.current_responsible_enterprise_id, b.lifecycle_status, b.duplicate_status, b.version
                FROM battery b
                JOIN recycle_batch_battery rbb ON rbb.battery_id = b.id AND rbb.relation_status = 'ACTIVE'
                JOIN recycle_batch rb ON rb.id = rbb.batch_id
                WHERE b.enterprise_id = ?
                  AND rb.enterprise_id = ?
                  AND b.lifecycle_status IN ('PENDING_ACCEPTANCE', 'PENDING_SUPPLEMENT')
                  AND rb.batch_status IN ('PENDING_ACCEPTANCE', 'ACCEPTANCE_PROCESSING')
                ORDER BY rb.submitted_at, rbb.created_at, b.id
                """, this::mapBattery, currentUser.enterpriseId(), currentUser.enterpriseId());
    }

    public AcceptanceResult createAcceptance(CurrentUser currentUser, Long batteryId, AcceptanceCreateRequest request, String idempotencyKey, HttpServletRequest servletRequest) {
        String canonical = "battery=" + batteryId
                + ";acceptanceResult=" + normalizeBlank(request.acceptanceResult())
                + ";identityCheckResult=" + normalizeBlank(request.identityCheckResult())
                + ";appearanceCheckResult=" + normalizeBlank(request.appearanceCheckResult())
                + ";documentCheckResult=" + normalizeBlank(request.documentCheckResult())
                + ";acceptanceNote=" + normalizeBlank(request.acceptanceNote());
        return idempotencyService.execute("CREATE_ACCEPTANCE", idempotencyKey, canonical, AcceptanceResult.class,
                () -> createAcceptanceInTransaction(currentUser, batteryId, request, servletRequest));
    }

    public AcceptanceResult createAcceptanceInTransaction(CurrentUser currentUser, Long batteryId, AcceptanceCreateRequest request, HttpServletRequest servletRequest) {
        validateAcceptanceRequest(currentUser, batteryId, request, servletRequest);
        BatteryDto battery = batteryService.requireBatteryForUpdate(currentUser, batteryId, servletRequest);
        if (!"PENDING_ACCEPTANCE".equals(battery.lifecycleStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_INVALID_BATTERY_STATE", "BATTERY", batteryId, "FAILED", "非待验收电池不能登记验收", servletRequest);
            throw ApiException.conflict("INVALID_BATTERY_STATE", "只有待验收电池可以登记验收");
        }
        long batchId = activeBatchIdForUpdate(currentUser, batteryId, servletRequest);
        String nextStatus = nextStatus(request.acceptanceResult());
        long acceptanceId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO acceptance_record (
                  id, enterprise_id, battery_id, batch_id, acceptance_result, identity_check_result,
                  appearance_check_result, document_check_result, acceptance_note, accepted_by,
                  accepted_at, created_at, version
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                acceptanceId,
                currentUser.enterpriseId(),
                batteryId,
                batchId,
                request.acceptanceResult().trim(),
                request.identityCheckResult().trim(),
                request.appearanceCheckResult().trim(),
                request.documentCheckResult().trim(),
                normalizeBlank(request.acceptanceNote()),
                currentUser.id());
        int updated = jdbcTemplate.update("""
                UPDATE battery
                SET lifecycle_status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND enterprise_id = ? AND lifecycle_status = 'PENDING_ACCEPTANCE'
                """, nextStatus, currentUser.id(), batteryId, currentUser.enterpriseId());
        if (updated != 1) {
            throw ApiException.conflict("INVALID_BATTERY_STATE", "电池验收状态已变化");
        }
        batteryService.insertLifecycle(currentUser, batteryId, batchId, eventType(request.acceptanceResult()), eventName(request.acceptanceResult()), "PENDING_ACCEPTANCE", nextStatus, servletRequest);
        auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_CREATED", "ACCEPTANCE_RECORD", acceptanceId, "batteryId=" + batteryId + ";result=" + request.acceptanceResult(), servletRequest);
        refreshBatchProgress(currentUser, batchId);
        return new AcceptanceResult(acceptanceId, nextStatus);
    }

    public BatteryDto supplement(CurrentUser currentUser, Long batteryId, AcceptanceSupplementRequest request, String idempotencyKey, HttpServletRequest servletRequest) {
        String canonical = "battery=" + batteryId
                + ";supplementNote=" + normalizeBlank(request.supplementNote())
                + ";attachmentIds=" + (request.attachmentIds() == null ? List.of() : request.attachmentIds());
        return idempotencyService.execute("SUPPLEMENT_ACCEPTANCE", idempotencyKey, canonical, BatteryDto.class,
                () -> supplementInTransaction(currentUser, batteryId, request, servletRequest));
    }

    public BatteryDto supplementInTransaction(CurrentUser currentUser, Long batteryId, AcceptanceSupplementRequest request, HttpServletRequest servletRequest) {
        String note = normalizeBlank(request.supplementNote());
        List<Long> attachmentIds = request.attachmentIds() == null ? List.of() : request.attachmentIds();
        if (note == null && attachmentIds.isEmpty()) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_SUPPLEMENT_REJECTED", "BATTERY", batteryId, "FAILED", "补充说明和附件至少提供一项", servletRequest);
            throw ApiException.badRequest("ACCEPTANCE_REQUIRED_FIELD_MISSING", "补充说明和附件至少提供一项");
        }
        if (note != null && note.length() > 500) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_SUPPLEMENT_REJECTED", "BATTERY", batteryId, "FAILED", "补充说明最长 500 字符", servletRequest);
            throw ApiException.badRequest("ACCEPTANCE_FIELD_TOO_LONG", "补充说明最长 500 字符");
        }
        BatteryDto battery = batteryService.requireBatteryForUpdate(currentUser, batteryId, servletRequest);
        if (!"PENDING_SUPPLEMENT".equals(battery.lifecycleStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_SUPPLEMENT_INVALID_STATE", "BATTERY", batteryId, "FAILED", "非待补充资料电池不能补充", servletRequest);
            throw ApiException.conflict("INVALID_BATTERY_STATE", "只有待补充资料电池可以提交补充");
        }
        long batchId = activeBatchIdForUpdate(currentUser, batteryId, servletRequest);
        Long acceptanceRecordId = latestNeedSupplementAcceptance(currentUser, batteryId);
        validateTempAttachmentsForUpdate(currentUser, batteryId, attachmentIds, servletRequest);
        long supplementId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO acceptance_supplement (
                  id, enterprise_id, battery_id, acceptance_record_id, supplement_note,
                  supplemented_by, supplemented_at, created_at, version
                )
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                """,
                supplementId,
                currentUser.enterpriseId(),
                batteryId,
                acceptanceRecordId,
                note == null ? "" : note,
                currentUser.id());
        bindAttachments(currentUser, attachmentIds, supplementId);
        int updated = jdbcTemplate.update("""
                UPDATE battery
                SET lifecycle_status = 'PENDING_ACCEPTANCE', updated_by = ?, updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND enterprise_id = ? AND lifecycle_status = 'PENDING_SUPPLEMENT'
                """, currentUser.id(), batteryId, currentUser.enterpriseId());
        if (updated != 1) {
            throw ApiException.conflict("INVALID_BATTERY_STATE", "电池补充状态已变化");
        }
        batteryService.insertLifecycle(currentUser, batteryId, batchId, "ACCEPTANCE_SUPPLEMENTED", "验收资料已补充", "PENDING_SUPPLEMENT", "PENDING_ACCEPTANCE", servletRequest);
        auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_SUPPLEMENTED", "ACCEPTANCE_SUPPLEMENT", supplementId, "batteryId=" + batteryId + ";attachmentCount=" + attachmentIds.size(), servletRequest);
        refreshBatchProgress(currentUser, batchId);
        return batteryService.requireBattery(currentUser, batteryId, servletRequest);
    }

    public void rejectDelete(CurrentUser currentUser, Long acceptanceRecordId, HttpServletRequest servletRequest) {
        Integer own = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM acceptance_record WHERE id = ? AND enterprise_id = ?", Integer.class, acceptanceRecordId, currentUser.enterpriseId());
        if (own != null && own > 0) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_RECORD_DELETE_FORBIDDEN", "ACCEPTANCE_RECORD", acceptanceRecordId, "FORBIDDEN", "已生效验收记录禁止删除", servletRequest);
            throw ApiException.conflict("EFFECTIVE_RECORD_DELETE_FORBIDDEN", "已生效验收记录禁止删除");
        }
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM acceptance_record WHERE id = ?", Integer.class, acceptanceRecordId);
        if (exists != null && exists > 0) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_RECORD_DELETE_CROSS_ENTERPRISE_DENIED", "ACCEPTANCE_RECORD", acceptanceRecordId, "FORBIDDEN", "拒绝跨企业验收记录访问", servletRequest);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的验收记录");
        }
        throw ApiException.notFound("验收记录不存在");
    }

    private void validateAcceptanceRequest(CurrentUser currentUser, Long batteryId, AcceptanceCreateRequest request, HttpServletRequest servletRequest) {
        if (normalizeBlank(request.acceptanceResult()) == null
                || normalizeBlank(request.identityCheckResult()) == null
                || normalizeBlank(request.appearanceCheckResult()) == null
                || normalizeBlank(request.documentCheckResult()) == null) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_REJECTED_BY_VALIDATION", "BATTERY", batteryId, "FAILED", "验收结果和三项检查结果均不能为空", servletRequest);
            throw ApiException.badRequest("ACCEPTANCE_REQUIRED_FIELD_MISSING", "验收结果和三项检查结果均不能为空");
        }
        String result = request.acceptanceResult().trim();
        if (!List.of("PASS", "NEED_SUPPLEMENT", "REJECT").contains(result)) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_REJECTED_BY_VALIDATION", "BATTERY", batteryId, "FAILED", "验收结果不合法", servletRequest);
            throw ApiException.badRequest("VALIDATION_FAILED", "验收结果不合法");
        }
        if (request.identityCheckResult().trim().length() > 40
                || request.appearanceCheckResult().trim().length() > 40
                || request.documentCheckResult().trim().length() > 40) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_REJECTED_BY_VALIDATION", "BATTERY", batteryId, "FAILED", "三项检查结果最长 40 字符", servletRequest);
            throw ApiException.badRequest("ACCEPTANCE_FIELD_TOO_LONG", "三项检查结果最长 40 字符");
        }
        String note = normalizeBlank(request.acceptanceNote());
        if (("NEED_SUPPLEMENT".equals(result) || "REJECT".equals(result)) && note == null) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_REJECTED_BY_VALIDATION", "BATTERY", batteryId, "FAILED", "待补充资料和验收不通过必须填写说明", servletRequest);
            throw ApiException.badRequest("ACCEPTANCE_REQUIRED_FIELD_MISSING", "待补充资料和验收不通过必须填写说明");
        }
        if (note != null && note.length() > 500) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_REJECTED_BY_VALIDATION", "BATTERY", batteryId, "FAILED", "验收说明最长 500 字符", servletRequest);
            throw ApiException.badRequest("ACCEPTANCE_FIELD_TOO_LONG", "验收说明最长 500 字符");
        }
    }

    private long activeBatchIdForUpdate(CurrentUser currentUser, Long batteryId, HttpServletRequest request) {
        List<Long> batchIds = jdbcTemplate.queryForList("""
                SELECT rb.id
                FROM recycle_batch rb
                JOIN recycle_batch_battery rbb ON rbb.batch_id = rb.id
                WHERE rb.enterprise_id = ?
                  AND rbb.enterprise_id = ?
                  AND rbb.battery_id = ?
                  AND rbb.relation_status = 'ACTIVE'
                  AND rb.batch_status IN ('PENDING_ACCEPTANCE', 'ACCEPTANCE_PROCESSING')
                FOR UPDATE
                """, Long.class, currentUser.enterpriseId(), currentUser.enterpriseId(), batteryId);
        if (batchIds.isEmpty()) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_BATCH_NOT_READY", "BATTERY", batteryId, "FAILED", "电池不在可验收批次中", request);
            throw ApiException.conflict("INVALID_BATCH_STATE", "电池不在可验收批次中");
        }
        return batchIds.get(0);
    }

    private void refreshBatchProgress(CurrentUser currentUser, Long batchId) {
        jdbcTemplate.queryForList("SELECT id FROM recycle_batch WHERE id = ? AND enterprise_id = ? FOR UPDATE", Long.class, batchId, currentUser.enterpriseId());
        String currentStatus = jdbcTemplate.queryForObject("SELECT batch_status FROM recycle_batch WHERE id = ? AND enterprise_id = ?", String.class, batchId, currentUser.enterpriseId());
        if ("COMPLETED".equals(currentStatus)) {
            return;
        }
        Integer pending = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM battery b
                JOIN recycle_batch_battery rbb ON rbb.battery_id = b.id
                WHERE rbb.enterprise_id = ? AND rbb.batch_id = ? AND rbb.relation_status = 'ACTIVE'
                  AND b.lifecycle_status IN ('PENDING_ACCEPTANCE', 'PENDING_SUPPLEMENT')
                """, Integer.class, currentUser.enterpriseId(), batchId);
        String nextStatus = pending != null && pending == 0 ? "COMPLETED" : "ACCEPTANCE_PROCESSING";
        jdbcTemplate.update("""
                UPDATE recycle_batch
                SET batch_status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND enterprise_id = ? AND batch_status <> ?
                """, nextStatus, currentUser.id(), batchId, currentUser.enterpriseId(), nextStatus);
    }

    private Long latestNeedSupplementAcceptance(CurrentUser currentUser, Long batteryId) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id
                FROM acceptance_record
                WHERE enterprise_id = ? AND battery_id = ? AND acceptance_result = 'NEED_SUPPLEMENT'
                ORDER BY accepted_at DESC, id DESC
                LIMIT 1
                """, Long.class, currentUser.enterpriseId(), batteryId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void validateTempAttachmentsForUpdate(CurrentUser currentUser, Long batteryId, List<Long> attachmentIds, HttpServletRequest servletRequest) {
        if (attachmentIds.isEmpty()) {
            return;
        }
        List<Long> found = jdbcTemplate.queryForList("""
                SELECT id
                FROM business_attachment
                WHERE enterprise_id = ?
                  AND uploaded_by = ?
                  AND binding_status = 'TEMP'
                  AND expires_at > CURRENT_TIMESTAMP(3)
                  AND id IN (%s)
                FOR UPDATE
                """.formatted(placeholders(attachmentIds.size())), Long.class, concat(currentUser.enterpriseId(), currentUser.id(), attachmentIds));
        if (found.size() != attachmentIds.size() || !found.containsAll(attachmentIds)) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_SUPPLEMENT_INVALID_ATTACHMENT", "BATTERY", batteryId, "FAILED", "附件不存在、已绑定、过期或无权使用", servletRequest);
            throw ApiException.badRequest("INVALID_ATTACHMENT", "附件不存在、已绑定、过期或无权使用");
        }
    }

    private void bindAttachments(CurrentUser currentUser, List<Long> attachmentIds, Long supplementId) {
        if (attachmentIds.isEmpty()) {
            return;
        }
        int updated = jdbcTemplate.update("""
                UPDATE business_attachment
                SET binding_status = 'BOUND', object_type = 'ACCEPTANCE_SUPPLEMENT', object_id = ?, expires_at = NULL
                WHERE enterprise_id = ?
                  AND uploaded_by = ?
                  AND binding_status = 'TEMP'
                  AND id IN (%s)
                """.formatted(placeholders(attachmentIds.size())), concat(supplementId, currentUser.enterpriseId(), currentUser.id(), attachmentIds));
        if (updated != attachmentIds.size()) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ACCEPTANCE_SUPPLEMENT_INVALID_ATTACHMENT", "ACCEPTANCE_SUPPLEMENT", supplementId, "FAILED", "附件绑定失败", null);
            throw ApiException.badRequest("INVALID_ATTACHMENT", "附件绑定失败");
        }
    }

    private String nextStatus(String result) {
        return switch (result.trim()) {
            case "PASS" -> "ACCEPTED_PENDING_INBOUND";
            case "NEED_SUPPLEMENT" -> "PENDING_SUPPLEMENT";
            case "REJECT" -> "ACCEPTANCE_REJECTED";
            default -> throw ApiException.badRequest("VALIDATION_FAILED", "验收结果不合法");
        };
    }

    private String eventType(String result) {
        return switch (result.trim()) {
            case "PASS" -> "ACCEPTANCE_PASSED";
            case "NEED_SUPPLEMENT" -> "ACCEPTANCE_NEED_SUPPLEMENT";
            case "REJECT" -> "ACCEPTANCE_REJECTED";
            default -> "ACCEPTANCE_UNKNOWN";
        };
    }

    private String eventName(String result) {
        return switch (result.trim()) {
            case "PASS" -> "验收通过";
            case "NEED_SUPPLEMENT" -> "验收待补充资料";
            case "REJECT" -> "验收不通过";
            default -> "验收";
        };
    }

    private BatteryDto mapBattery(ResultSet rs, int rowNum) throws SQLException {
        return new BatteryDto(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getString("system_trace_code"),
                rs.getString("original_code"),
                rs.getString("battery_type"),
                rs.getString("battery_model"),
                rs.getString("manufacturer"),
                rs.getString("battery_chemistry"),
                rs.getBigDecimal("nominal_capacity"),
                rs.getObject("production_date", java.time.LocalDate.class),
                rs.getLong("current_responsible_enterprise_id"),
                rs.getString("lifecycle_status"),
                rs.getString("duplicate_status"),
                rs.getInt("version"));
    }

    private Object[] concat(Object first, Object second, List<Long> rest) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.add(second);
        args.addAll(rest);
        return args.toArray();
    }

    private Object[] concat(Object first, Object second, Object third, List<Long> rest) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.add(second);
        args.add(third);
        args.addAll(rest);
        return args.toArray();
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
