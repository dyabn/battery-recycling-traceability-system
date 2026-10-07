package com.batteryrecycling.traceability.batch;

import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.battery.BatteryDtos.BatteryDto;
import com.batteryrecycling.traceability.battery.BatteryService;
import com.batteryrecycling.traceability.batch.RecycleBatchDtos.AddBatteryRequest;
import com.batteryrecycling.traceability.batch.RecycleBatchDtos.RecycleBatchCreateRequest;
import com.batteryrecycling.traceability.batch.RecycleBatchDtos.RecycleBatchDto;
import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.idempotency.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RecycleBatchService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final BatteryService batteryService;

    public RecycleBatchService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, IdempotencyService idempotencyService, AuditService auditService, BatteryService batteryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.batteryService = batteryService;
    }

    public RecycleBatchDto create(CurrentUser currentUser, RecycleBatchCreateRequest request, String idempotencyKey, HttpServletRequest servletRequest) {
        return idempotencyService.execute("CREATE_RECYCLE_BATCH", idempotencyKey, canonicalRequest(request), RecycleBatchDto.class,
                () -> createInTransaction(currentUser, request, servletRequest));
    }

    public RecycleBatchDto createInTransaction(CurrentUser currentUser, RecycleBatchCreateRequest request, HttpServletRequest servletRequest) {
        validateRequest(request);
        validateAttachments(currentUser, request.attachmentIds());
        long batchId = idGenerator.nextId();
        String batchNo = "RB-" + batchId;
        jdbcTemplate.update("""
                INSERT INTO recycle_batch (
                  id, enterprise_id, batch_no, source_type, source_subject_name, handover_date,
                  handover_location, related_document_no, handover_person, remark, batch_status,
                  submitted_at, created_by, created_at, updated_by, updated_at, version
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', NULL, ?, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0)
                """,
                batchId,
                currentUser.enterpriseId(),
                batchNo,
                request.sourceType().trim(),
                request.sourceSubjectName().trim(),
                request.handoverDate(),
                normalizeBlank(request.handoverLocation()),
                normalizeBlank(request.relatedDocumentNo()),
                normalizeBlank(request.handoverPerson()),
                normalizeBlank(request.remark()),
                currentUser.id());
        bindAttachments(currentUser, request.attachmentIds(), "RECYCLE_BATCH", batchId);
        auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "RECYCLE_BATCH_CREATED", "RECYCLE_BATCH", batchId, "batchNo=" + batchNo, servletRequest);
        return detail(currentUser, batchId, servletRequest);
    }

    public List<RecycleBatchDto> list(CurrentUser currentUser, String status) {
        String normalizedStatus = normalizeBlank(status);
        List<Long> ids;
        if (normalizedStatus == null) {
            ids = jdbcTemplate.queryForList("""
                    SELECT id
                    FROM recycle_batch
                    WHERE enterprise_id = ?
                    ORDER BY created_at DESC, id DESC
                    """, Long.class, currentUser.enterpriseId());
        } else {
            ids = jdbcTemplate.queryForList("""
                    SELECT id
                    FROM recycle_batch
                    WHERE enterprise_id = ? AND batch_status = ?
                    ORDER BY created_at DESC, id DESC
                    """, Long.class, currentUser.enterpriseId(), normalizedStatus);
        }
        List<RecycleBatchDto> batches = new ArrayList<>();
        for (Long id : ids) {
            batches.add(detail(currentUser, id, null));
        }
        return batches;
    }

    public RecycleBatchDto detail(CurrentUser currentUser, Long batchId, HttpServletRequest servletRequest) {
        RecycleBatchRecord record = requireBatch(currentUser, batchId, servletRequest);
        return toDto(record, batteriesForBatch(currentUser, batchId));
    }

    public RecycleBatchDto update(CurrentUser currentUser, Long batchId, RecycleBatchCreateRequest request, String idempotencyKey, HttpServletRequest servletRequest) {
        String canonical = "batch=" + batchId + ";" + canonicalRequest(request);
        return idempotencyService.execute("UPDATE_RECYCLE_BATCH", idempotencyKey, canonical, RecycleBatchDto.class,
                () -> updateInTransaction(currentUser, batchId, request, servletRequest));
    }

    public RecycleBatchDto updateInTransaction(CurrentUser currentUser, Long batchId, RecycleBatchCreateRequest request, HttpServletRequest servletRequest) {
        validateRequest(request);
        validateAttachments(currentUser, request.attachmentIds());
        RecycleBatchRecord batch = requireBatchForUpdate(currentUser, batchId, servletRequest);
        if (!"DRAFT".equals(batch.batchStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "RECYCLE_BATCH_UPDATE_CONFLICT", "RECYCLE_BATCH", batchId, "FAILED", "非草稿批次禁止修改", servletRequest);
            throw ApiException.conflict("INVALID_BATCH_STATE", "只有草稿批次可以修改");
        }
        jdbcTemplate.update("""
                UPDATE recycle_batch
                SET source_type = ?, source_subject_name = ?, handover_date = ?,
                    handover_location = ?, related_document_no = ?, handover_person = ?,
                    remark = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND enterprise_id = ? AND batch_status = 'DRAFT' AND version = ?
                """,
                request.sourceType().trim(),
                request.sourceSubjectName().trim(),
                request.handoverDate(),
                normalizeBlank(request.handoverLocation()),
                normalizeBlank(request.relatedDocumentNo()),
                normalizeBlank(request.handoverPerson()),
                normalizeBlank(request.remark()),
                currentUser.id(),
                batchId,
                currentUser.enterpriseId(),
                batch.version());
        bindAttachments(currentUser, request.attachmentIds(), "RECYCLE_BATCH", batchId);
        auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "RECYCLE_BATCH_UPDATED", "RECYCLE_BATCH", batchId, "version=" + batch.version() + "->" + (batch.version() + 1), servletRequest);
        return detail(currentUser, batchId, servletRequest);
    }

    public RecycleBatchDto addBattery(CurrentUser currentUser, Long batchId, AddBatteryRequest request, String idempotencyKey, HttpServletRequest servletRequest) {
        String canonical = "batch=" + batchId + ";battery=" + request.batteryId();
        return idempotencyService.execute("ADD_BATTERY_TO_RECYCLE_BATCH", idempotencyKey, canonical, RecycleBatchDto.class,
                () -> addBatteryInTransaction(currentUser, batchId, request.batteryId(), servletRequest));
    }

    public RecycleBatchDto addBatteryInTransaction(CurrentUser currentUser, Long batchId, Long batteryId, HttpServletRequest servletRequest) {
        RecycleBatchRecord batch = requireBatchForUpdate(currentUser, batchId, servletRequest);
        if (!"DRAFT".equals(batch.batchStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "BATCH_BATTERY_ADD_CONFLICT", "RECYCLE_BATCH", batchId, "FAILED", "非草稿批次不能加入电池", servletRequest);
            throw ApiException.conflict("INVALID_BATCH_STATE", "只有草稿批次可以加入电池");
        }
        BatteryDto battery = batteryService.requireBatteryForUpdate(currentUser, batteryId, servletRequest);
        if (!"REGISTERED".equals(battery.lifecycleStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "BATCH_BATTERY_ADD_INVALID_STATE", "BATTERY", batteryId, "FAILED", "电池状态不允许加入批次", servletRequest);
            throw ApiException.conflict("INVALID_BATTERY_STATE", "只有已登记电池可以加入批次");
        }
        validateDuplicateReady(currentUser, battery, servletRequest);
        List<Long> activeBatchIds = jdbcTemplate.queryForList("""
                SELECT batch_id
                FROM recycle_batch_battery
                WHERE enterprise_id = ? AND battery_id = ? AND relation_status = 'ACTIVE'
                FOR UPDATE
                """, Long.class, currentUser.enterpriseId(), batteryId);
        if (activeBatchIds.contains(batchId)) {
            return detail(currentUser, batchId, servletRequest);
        }
        if (!activeBatchIds.isEmpty()) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "BATTERY_ALREADY_IN_ACTIVE_BATCH", "BATTERY", batteryId, "FAILED", "电池已经加入其他有效批次", servletRequest);
            throw ApiException.conflict("BATTERY_ALREADY_IN_ACTIVE_BATCH", "电池已经加入其他有效批次");
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO recycle_batch_battery (
                      id, enterprise_id, batch_id, battery_id, relation_status, created_by, created_at
                    )
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP(3))
                    """,
                    idGenerator.nextId(),
                    currentUser.enterpriseId(),
                    batchId,
                    batteryId,
                    currentUser.id());
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("DUPLICATE_SUBMISSION", "电池已经加入有效批次");
        }
        auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "BATTERY_ADDED_TO_BATCH", "RECYCLE_BATCH", batchId, "batteryId=" + batteryId, servletRequest);
        return detail(currentUser, batchId, servletRequest);
    }

    public RecycleBatchDto submit(CurrentUser currentUser, Long batchId, String idempotencyKey, HttpServletRequest servletRequest) {
        return idempotencyService.execute("SUBMIT_RECYCLE_BATCH", idempotencyKey, "batch=" + batchId, RecycleBatchDto.class,
                () -> submitInTransaction(currentUser, batchId, servletRequest));
    }

    public RecycleBatchDto submitInTransaction(CurrentUser currentUser, Long batchId, HttpServletRequest servletRequest) {
        RecycleBatchRecord batch = requireBatchForUpdate(currentUser, batchId, servletRequest);
        if (!"DRAFT".equals(batch.batchStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "RECYCLE_BATCH_SUBMIT_CONFLICT", "RECYCLE_BATCH", batchId, "FAILED", "非草稿批次不能提交", servletRequest);
            throw ApiException.conflict("INVALID_BATCH_STATE", "只有草稿批次可以提交");
        }
        if (batch.sourceType().isBlank() || batch.sourceSubjectName().isBlank() || batch.handoverDate() == null) {
            throw ApiException.badRequest("BATCH_REQUIRED_FIELD_MISSING", "批次来源必填信息缺失");
        }
        List<BatteryDto> batteries = batteriesForBatchForUpdate(currentUser, batchId);
        if (batteries.isEmpty()) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "RECYCLE_BATCH_SUBMIT_EMPTY", "RECYCLE_BATCH", batchId, "FAILED", "空批次不能提交", servletRequest);
            throw ApiException.badRequest("BATCH_EMPTY", "空批次不能提交验收");
        }
        for (BatteryDto battery : batteries) {
            if (!"REGISTERED".equals(battery.lifecycleStatus())) {
                auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "RECYCLE_BATCH_SUBMIT_INVALID_BATTERY", "BATTERY", battery.id(), "FAILED", "批次存在非登记状态电池", servletRequest);
                throw ApiException.conflict("INVALID_BATTERY_STATE", "批次内所有电池必须为已登记状态");
            }
            validateDuplicateReady(currentUser, battery, servletRequest);
        }
        int batchUpdated = jdbcTemplate.update("""
                UPDATE recycle_batch
                SET batch_status = 'PENDING_ACCEPTANCE', submitted_at = CURRENT_TIMESTAMP(3),
                    updated_by = ?, updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND enterprise_id = ? AND batch_status = 'DRAFT' AND version = ?
                """, currentUser.id(), batchId, currentUser.enterpriseId(), batch.version());
        if (batchUpdated != 1) {
            throw ApiException.conflict("OPTIMISTIC_LOCK_CONFLICT", "批次已经被其他操作修改");
        }
        for (BatteryDto battery : batteries) {
            int updated = jdbcTemplate.update("""
                    UPDATE battery
                    SET lifecycle_status = 'PENDING_ACCEPTANCE', updated_by = ?,
                        updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                    WHERE id = ? AND enterprise_id = ? AND lifecycle_status = 'REGISTERED'
                    """, currentUser.id(), battery.id(), currentUser.enterpriseId());
            if (updated != 1) {
                throw ApiException.conflict("INVALID_BATTERY_STATE", "批次内电池状态已变化");
            }
            batteryService.insertLifecycle(currentUser, battery.id(), batchId, "BATCH_SUBMITTED", "批次提交待验收", "REGISTERED", "PENDING_ACCEPTANCE", servletRequest);
        }
        auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "RECYCLE_BATCH_SUBMITTED", "RECYCLE_BATCH", batchId, "batteryCount=" + batteries.size(), servletRequest);
        return detail(currentUser, batchId, servletRequest);
    }

    private RecycleBatchRecord requireBatch(CurrentUser currentUser, Long batchId, HttpServletRequest request) {
        List<RecycleBatchRecord> batches = jdbcTemplate.query("""
                SELECT id, enterprise_id, batch_no, source_type, source_subject_name, handover_date,
                       handover_location, related_document_no, handover_person, remark, batch_status,
                       submitted_at, created_by, created_at, updated_by, updated_at, version
                FROM recycle_batch
                WHERE id = ? AND enterprise_id = ?
                """, this::mapBatch, batchId, currentUser.enterpriseId());
        if (!batches.isEmpty()) {
            return batches.get(0);
        }
        if (existsBatch(batchId)) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "BATCH_CROSS_ENTERPRISE_DENIED", "RECYCLE_BATCH", batchId, "FORBIDDEN", "拒绝跨企业批次访问", request);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的批次");
        }
        throw ApiException.notFound("回收批次不存在");
    }

    private RecycleBatchRecord requireBatchForUpdate(CurrentUser currentUser, Long batchId, HttpServletRequest request) {
        List<RecycleBatchRecord> batches = jdbcTemplate.query("""
                SELECT id, enterprise_id, batch_no, source_type, source_subject_name, handover_date,
                       handover_location, related_document_no, handover_person, remark, batch_status,
                       submitted_at, created_by, created_at, updated_by, updated_at, version
                FROM recycle_batch
                WHERE id = ? AND enterprise_id = ?
                FOR UPDATE
                """, this::mapBatch, batchId, currentUser.enterpriseId());
        if (!batches.isEmpty()) {
            return batches.get(0);
        }
        return requireBatch(currentUser, batchId, request);
    }

    private List<BatteryDto> batteriesForBatch(CurrentUser currentUser, Long batchId) {
        return jdbcTemplate.query("""
                SELECT b.id, b.enterprise_id, b.system_trace_code, b.original_code, b.battery_type, b.battery_model,
                       b.manufacturer, b.battery_chemistry, b.nominal_capacity, b.production_date,
                       b.current_responsible_enterprise_id, b.lifecycle_status, b.duplicate_status, b.version
                FROM battery b
                JOIN recycle_batch_battery rbb ON rbb.battery_id = b.id
                WHERE rbb.enterprise_id = ? AND rbb.batch_id = ? AND rbb.relation_status = 'ACTIVE'
                ORDER BY rbb.created_at, b.id
                """, this::mapBattery, currentUser.enterpriseId(), batchId);
    }

    private List<BatteryDto> batteriesForBatchForUpdate(CurrentUser currentUser, Long batchId) {
        return jdbcTemplate.query("""
                SELECT b.id, b.enterprise_id, b.system_trace_code, b.original_code, b.battery_type, b.battery_model,
                       b.manufacturer, b.battery_chemistry, b.nominal_capacity, b.production_date,
                       b.current_responsible_enterprise_id, b.lifecycle_status, b.duplicate_status, b.version
                FROM battery b
                JOIN recycle_batch_battery rbb ON rbb.battery_id = b.id
                WHERE rbb.enterprise_id = ? AND rbb.batch_id = ? AND rbb.relation_status = 'ACTIVE'
                ORDER BY rbb.created_at, b.id
                FOR UPDATE
                """, this::mapBattery, currentUser.enterpriseId(), batchId);
    }

    private void validateRequest(RecycleBatchCreateRequest request) {
        if (request.sourceType() == null || request.sourceType().isBlank()
                || request.sourceSubjectName() == null || request.sourceSubjectName().isBlank()
                || request.handoverDate() == null) {
            throw ApiException.badRequest("BATCH_REQUIRED_FIELD_MISSING", "批次来源必填信息缺失");
        }
    }

    private void validateDuplicateReady(CurrentUser currentUser, BatteryDto battery, HttpServletRequest request) {
        if ("SUSPECTED_DUPLICATE".equals(battery.duplicateStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "BATTERY_DUPLICATE_UNRESOLVED", "BATTERY", battery.id(), "FAILED", "疑似重复未核实", request);
            throw ApiException.conflict("BATTERY_DUPLICATE_UNRESOLVED", "疑似重复未核实");
        }
        if (!("NORMAL".equals(battery.duplicateStatus()) || "RESOLVED_DIFFERENT".equals(battery.duplicateStatus()) || "RESOLVED_SAME".equals(battery.duplicateStatus()))) {
            throw ApiException.conflict("BATTERY_DUPLICATE_UNRESOLVED", "电池重复状态不允许当前操作");
        }
        if (battery.originalCode() == null || battery.originalCode().isBlank()) {
            return;
        }
        Integer pendingCandidates = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM battery_registration_candidate
                WHERE enterprise_id = ?
                  AND original_code = ?
                  AND candidate_status = 'PENDING_REVIEW'
                """, Integer.class, currentUser.enterpriseId(), battery.originalCode());
        if (pendingCandidates != null && pendingCandidates > 0) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "BATTERY_DUPLICATE_UNRESOLVED", "BATTERY", battery.id(), "FAILED", "存在未关闭重复编码候选", request);
            throw ApiException.conflict("BATTERY_DUPLICATE_UNRESOLVED", "存在未关闭重复编码候选，不能加入批次或提交待验收");
        }
    }

    private void validateAttachments(CurrentUser currentUser, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM business_attachment
                WHERE enterprise_id = ?
                  AND uploaded_by = ?
                  AND binding_status = 'TEMP'
                  AND expires_at > CURRENT_TIMESTAMP(3)
                  AND id IN (%s)
                """.formatted(placeholders(attachmentIds.size())), Integer.class, concat(currentUser.enterpriseId(), currentUser.id(), attachmentIds));
        if (count == null || count != attachmentIds.size()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "附件不存在、已绑定、过期或无权使用");
        }
    }

    private void bindAttachments(CurrentUser currentUser, List<Long> attachmentIds, String objectType, Long objectId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        Object[] args = concat(objectType, objectId, currentUser.enterpriseId(), currentUser.id(), attachmentIds);
        jdbcTemplate.update("""
                UPDATE business_attachment
                SET binding_status = 'BOUND', object_type = ?, object_id = ?, expires_at = NULL
                WHERE enterprise_id = ?
                  AND uploaded_by = ?
                  AND binding_status = 'TEMP'
                  AND id IN (%s)
                """.formatted(placeholders(attachmentIds.size())), args);
    }

    private String canonicalRequest(RecycleBatchCreateRequest request) {
        return "sourceType=" + normalizeBlank(request.sourceType())
                + ";sourceSubjectName=" + normalizeBlank(request.sourceSubjectName())
                + ";handoverDate=" + request.handoverDate()
                + ";handoverLocation=" + normalizeBlank(request.handoverLocation())
                + ";relatedDocumentNo=" + normalizeBlank(request.relatedDocumentNo())
                + ";handoverPerson=" + normalizeBlank(request.handoverPerson())
                + ";remark=" + normalizeBlank(request.remark())
                + ";attachmentIds=" + (request.attachmentIds() == null ? List.of() : request.attachmentIds());
    }

    private RecycleBatchDto toDto(RecycleBatchRecord batch, List<BatteryDto> batteries) {
        return new RecycleBatchDto(
                batch.id(),
                batch.enterpriseId(),
                batch.batchNo(),
                batch.sourceType(),
                batch.sourceSubjectName(),
                batch.handoverDate(),
                batch.handoverLocation(),
                batch.relatedDocumentNo(),
                batch.handoverPerson(),
                batch.remark(),
                batch.batchStatus(),
                batch.submittedAt(),
                batch.createdBy(),
                batch.createdAt(),
                batch.updatedBy(),
                batch.updatedAt(),
                batch.version(),
                batteries);
    }

    private RecycleBatchRecord mapBatch(ResultSet rs, int rowNum) throws SQLException {
        return new RecycleBatchRecord(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getString("batch_no"),
                rs.getString("source_type"),
                rs.getString("source_subject_name"),
                rs.getObject("handover_date", LocalDate.class),
                rs.getString("handover_location"),
                rs.getString("related_document_no"),
                rs.getString("handover_person"),
                rs.getString("remark"),
                rs.getString("batch_status"),
                rs.getObject("submitted_at", LocalDateTime.class),
                rs.getLong("created_by"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_by", Long.class),
                rs.getObject("updated_at", LocalDateTime.class),
                rs.getInt("version"));
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
                rs.getObject("production_date", LocalDate.class),
                rs.getLong("current_responsible_enterprise_id"),
                rs.getString("lifecycle_status"),
                rs.getString("duplicate_status"),
                rs.getInt("version"));
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private Object[] concat(Object first, Object second, List<Long> rest) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.add(second);
        args.addAll(rest);
        return args.toArray();
    }

    private Object[] concat(Object first, Object second, Object third, Object fourth, List<Long> rest) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.add(second);
        args.add(third);
        args.add(fourth);
        args.addAll(rest);
        return args.toArray();
    }

    private boolean existsBatch(Long batchId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recycle_batch WHERE id = ?", Integer.class, batchId);
        return count != null && count > 0;
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record RecycleBatchRecord(
            Long id,
            Long enterpriseId,
            String batchNo,
            String sourceType,
            String sourceSubjectName,
            LocalDate handoverDate,
            String handoverLocation,
            String relatedDocumentNo,
            String handoverPerson,
            String remark,
            String batchStatus,
            LocalDateTime submittedAt,
            Long createdBy,
            LocalDateTime createdAt,
            Long updatedBy,
            LocalDateTime updatedAt,
            Integer version
    ) {
    }
}
