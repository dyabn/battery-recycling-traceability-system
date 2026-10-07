package com.batteryrecycling.traceability.battery;

import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.battery.BatteryDtos.BatteryCreateRequest;
import com.batteryrecycling.traceability.battery.BatteryDtos.BatteryDto;
import com.batteryrecycling.traceability.battery.BatteryDtos.BatteryRegistrationResult;
import com.batteryrecycling.traceability.battery.BatteryDtos.DuplicateCheckRequest;
import com.batteryrecycling.traceability.battery.BatteryDtos.DuplicateCheckResult;
import com.batteryrecycling.traceability.battery.BatteryDtos.DuplicateResolutionRequest;
import com.batteryrecycling.traceability.battery.BatteryDtos.TraceEventDto;
import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.idempotency.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BatteryService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    public BatteryService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, IdempotencyService idempotencyService, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
    }

    public BatteryRegistrationResult createBattery(CurrentUser currentUser, BatteryCreateRequest request, String idempotencyKey, HttpServletRequest servletRequest) {
        String canonical = "originalCode=" + normalizeBlank(request.originalCode())
                + ";batteryType=" + request.batteryType()
                + ";model=" + normalizeBlank(request.batteryModel())
                + ";manufacturer=" + normalizeBlank(request.manufacturer())
                + ";chemistry=" + request.batteryChemistry()
                + ";capacity=" + request.nominalCapacity()
                + ";productionDate=" + request.productionDate();
        return idempotencyService.execute("CREATE_BATTERY", idempotencyKey, canonical, BatteryRegistrationResult.class,
                () -> createBatteryInTransaction(currentUser, request, servletRequest));
    }

    public BatteryRegistrationResult createBatteryInTransaction(CurrentUser currentUser, BatteryCreateRequest request, HttpServletRequest servletRequest) {
        validateCreateRequest(request);
        String originalCode = normalizeBlank(request.originalCode());
        boolean locked = false;
        boolean releaseInFinally = false;
        String lockName = originalCode == null ? null : originalCodeLockName(currentUser.enterpriseId(), originalCode);
        try {
            if (lockName != null) {
                locked = acquireLock(lockName);
                releaseInFinally = !registerLockRelease(lockName);
            }
            if (originalCode != null) {
                List<Long> matched = matchedBatteryIds(currentUser.enterpriseId(), originalCode);
                if (!matched.isEmpty()) {
                    long candidateId = idGenerator.nextId();
                    jdbcTemplate.update("""
                            INSERT INTO battery_registration_candidate (
                              id, enterprise_id, original_code, battery_type, battery_model, manufacturer,
                              battery_chemistry, nominal_capacity, production_date, candidate_status,
                              submitted_by, submitted_at, created_at, updated_at, version
                            )
                            VALUES (?, ?, ?, 'PACK', ?, ?, ?, ?, ?, 'PENDING_REVIEW', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                            """,
                            candidateId,
                            currentUser.enterpriseId(),
                            originalCode,
                            normalizeBlank(request.batteryModel()),
                            normalizeBlank(request.manufacturer()),
                            normalizeBlank(request.batteryChemistry()),
                            request.nominalCapacity(),
                            request.productionDate(),
                            currentUser.id());
                    auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "BATTERY_CANDIDATE_CREATED", "BATTERY_REGISTRATION_CANDIDATE", candidateId, "matched=" + matched, servletRequest);
                    return BatteryRegistrationResult.duplicate(candidateId, matched);
                }
            }
            BatteryDto battery = insertBattery(currentUser, originalCode, request.batteryModel(), request.manufacturer(), request.batteryChemistry(), request.nominalCapacity(), request.productionDate(), "NORMAL", servletRequest);
            return BatteryRegistrationResult.created(battery);
        } finally {
            if (locked && releaseInFinally) {
                releaseLock(lockName);
            }
        }
    }

    public DuplicateCheckResult checkDuplicate(CurrentUser currentUser, DuplicateCheckRequest request) {
        String originalCode = normalizeBlank(request.originalCode());
        if (originalCode == null) {
            throw ApiException.badRequest("VALIDATION_FAILED", "原始编码不能为空");
        }
        List<Long> matched = matchedBatteryIds(currentUser.enterpriseId(), originalCode);
        return new DuplicateCheckResult(!matched.isEmpty(), matched);
    }

    public BatteryDto resolveDuplicate(CurrentUser currentUser, Long candidateId, DuplicateResolutionRequest request, String idempotencyKey, HttpServletRequest servletRequest) {
        String canonical = "candidate=" + candidateId
                + ";reviewResult=" + request.reviewResult()
                + ";existingBatteryId=" + request.existingBatteryId()
                + ";duplicateReason=" + normalizeBlank(request.duplicateReason());
        return idempotencyService.execute("RESOLVE_BATTERY_DUPLICATE", idempotencyKey, canonical, BatteryDto.class,
                () -> resolveDuplicateInTransaction(currentUser, candidateId, request, servletRequest));
    }

    public BatteryDto resolveDuplicateInTransaction(CurrentUser currentUser, Long candidateId, DuplicateResolutionRequest request, HttpServletRequest servletRequest) {
        Candidate candidate = candidateForUpdate(currentUser, candidateId, servletRequest);
        if (!"PENDING_REVIEW".equals(candidate.candidateStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "DUPLICATE_RESOLUTION_CONFLICT", "BATTERY_REGISTRATION_CANDIDATE", candidateId, "FAILED", "候选已经处理", servletRequest);
            throw ApiException.conflict("DUPLICATE_CANDIDATE_ALREADY_CLOSED", "重复编码候选已经处理");
        }
        if ("SAME_BATTERY".equals(request.reviewResult())) {
            if (request.existingBatteryId() == null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "确认同一电池时必须选择已有电池");
            }
            BatteryDto existing = requireBattery(currentUser, request.existingBatteryId(), servletRequest);
            if (candidate.originalCode() == null || !candidate.originalCode().equals(existing.originalCode())) {
                throw ApiException.badRequest("VALIDATION_FAILED", "已有电池与候选原始编码不一致");
            }
            closeCandidate(candidateId, "CLOSED_SAME");
            long reviewId = idGenerator.nextId();
            jdbcTemplate.update("""
                    INSERT INTO duplicate_code_review (
                      id, enterprise_id, candidate_id, original_code, existing_battery_id,
                      created_battery_id, review_result, duplicate_reason, reviewer_id,
                      reviewed_at, created_at, version
                    )
                    VALUES (?, ?, ?, ?, ?, NULL, 'SAME_BATTERY', NULL, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                    """,
                    reviewId,
                    currentUser.enterpriseId(),
                    candidateId,
                    candidate.originalCode(),
                    existing.id(),
                    currentUser.id());
            insertLifecycle(currentUser, existing.id(), null, "DUPLICATE_REVIEW_SAME", "重复编码核实为同一电池", existing.lifecycleStatus(), existing.lifecycleStatus(), servletRequest);
            auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "DUPLICATE_RESOLUTION_SAME", "BATTERY_REGISTRATION_CANDIDATE", candidateId, "existingBatteryId=" + existing.id(), servletRequest);
            return existing;
        }
        if ("DIFFERENT_BATTERY".equals(request.reviewResult())) {
            String reason = normalizeBlank(request.duplicateReason());
            if (reason == null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "确认不同电池时必须填写重复原因");
            }
            BatteryDto created = insertBattery(currentUser, candidate.originalCode(), candidate.batteryModel(), candidate.manufacturer(), candidate.batteryChemistry(), candidate.nominalCapacity(), candidate.productionDate(), "RESOLVED_DIFFERENT", servletRequest);
            closeCandidate(candidateId, "CLOSED_DIFFERENT");
            long reviewId = idGenerator.nextId();
            jdbcTemplate.update("""
                    INSERT INTO duplicate_code_review (
                      id, enterprise_id, candidate_id, original_code, existing_battery_id,
                      created_battery_id, review_result, duplicate_reason, reviewer_id,
                      reviewed_at, created_at, version
                    )
                    VALUES (?, ?, ?, ?, NULL, ?, 'DIFFERENT_BATTERY', ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                    """,
                    reviewId,
                    currentUser.enterpriseId(),
                    candidateId,
                    candidate.originalCode(),
                    created.id(),
                    reason,
                    currentUser.id());
            auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "DUPLICATE_RESOLUTION_DIFFERENT", "BATTERY_REGISTRATION_CANDIDATE", candidateId, "createdBatteryId=" + created.id(), servletRequest);
            return created;
        }
        throw ApiException.badRequest("VALIDATION_FAILED", "重复编码核实结论不合法");
    }

    public BatteryDto requireBattery(CurrentUser currentUser, Long batteryId, HttpServletRequest request) {
        List<BatteryDto> batteries = jdbcTemplate.query("""
                SELECT id, enterprise_id, system_trace_code, original_code, battery_type, battery_model,
                       manufacturer, battery_chemistry, nominal_capacity, production_date,
                       current_responsible_enterprise_id, lifecycle_status, duplicate_status, version
                FROM battery
                WHERE id = ? AND enterprise_id = ?
                """, this::mapBattery, batteryId, currentUser.enterpriseId());
        if (!batteries.isEmpty()) {
            return batteries.get(0);
        }
        if (exists("battery", batteryId)) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "BATTERY_CROSS_ENTERPRISE_DENIED", "BATTERY", batteryId, "FORBIDDEN", "拒绝跨企业电池访问", request);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的电池");
        }
        throw ApiException.notFound("电池不存在");
    }

    public BatteryDto requireBatteryForUpdate(CurrentUser currentUser, Long batteryId, HttpServletRequest request) {
        List<BatteryDto> batteries = jdbcTemplate.query("""
                SELECT id, enterprise_id, system_trace_code, original_code, battery_type, battery_model,
                       manufacturer, battery_chemistry, nominal_capacity, production_date,
                       current_responsible_enterprise_id, lifecycle_status, duplicate_status, version
                FROM battery
                WHERE id = ? AND enterprise_id = ?
                FOR UPDATE
                """, this::mapBattery, batteryId, currentUser.enterpriseId());
        if (!batteries.isEmpty()) {
            return batteries.get(0);
        }
        return requireBattery(currentUser, batteryId, request);
    }

    public List<TraceEventDto> trace(CurrentUser currentUser, Long batteryId, HttpServletRequest request) {
        BatteryDto battery = requireBattery(currentUser, batteryId, request);
        return jdbcTemplate.query("""
                SELECT e.event_name, b.system_trace_code, u.display_name, e.occurred_at,
                       CONCAT(COALESCE(e.from_status, 'null'), ' -> ', COALESCE(e.to_status, 'null')) AS status_change,
                       e.event_result
                FROM lifecycle_event e
                JOIN battery b ON b.id = e.battery_id
                JOIN sys_user u ON u.id = e.operator_user_id
                WHERE e.enterprise_id = ? AND e.battery_id = ?
                ORDER BY e.occurred_at, e.id
                """,
                (rs, rowNum) -> new TraceEventDto(
                        rs.getString("event_name"),
                        rs.getString("system_trace_code"),
                        rs.getString("display_name"),
                        rs.getTimestamp("occurred_at").toLocalDateTime(),
                        rs.getString("status_change"),
                        rs.getString("event_result")),
                currentUser.enterpriseId(), battery.id());
    }

    public void insertLifecycle(CurrentUser currentUser, Long batteryId, Long batchId, String eventType, String eventName, String fromStatus, String toStatus, HttpServletRequest request) {
        String traceId = request == null ? null : request.getHeader("X-Trace-Id");
        jdbcTemplate.update("""
                INSERT INTO lifecycle_event (
                  id, enterprise_id, battery_id, batch_id, event_type, event_name,
                  from_status, to_status, event_result, operator_user_id, occurred_at, remark, trace_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?, CURRENT_TIMESTAMP(3), NULL, ?)
                """,
                idGenerator.nextId(),
                currentUser.enterpriseId(),
                batteryId,
                batchId,
                eventType,
                eventName,
                fromStatus,
                toStatus,
                currentUser.id(),
                traceId);
    }

    private BatteryDto insertBattery(CurrentUser currentUser, String originalCode, String batteryModel, String manufacturer, String batteryChemistry, BigDecimal nominalCapacity, LocalDate productionDate, String duplicateStatus, HttpServletRequest request) {
        long batteryId = idGenerator.nextId();
        String traceCode = "BAT-" + batteryId;
        jdbcTemplate.update("""
                INSERT INTO battery (
                  id, enterprise_id, system_trace_code, original_code, battery_type, battery_model,
                  manufacturer, battery_chemistry, nominal_capacity, production_date,
                  current_responsible_enterprise_id, lifecycle_status, duplicate_status,
                  created_by, created_at, updated_by, updated_at, version
                )
                VALUES (?, ?, ?, ?, 'PACK', ?, ?, ?, ?, ?, ?, 'REGISTERED', ?, ?, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0)
                """,
                batteryId,
                currentUser.enterpriseId(),
                traceCode,
                originalCode,
                normalizeBlank(batteryModel),
                normalizeBlank(manufacturer),
                normalizeBlank(batteryChemistry),
                nominalCapacity,
                productionDate,
                currentUser.enterpriseId(),
                duplicateStatus,
                currentUser.id());
        insertLifecycle(currentUser, batteryId, null, "BATTERY_REGISTERED", "电池登记", null, "REGISTERED", request);
        auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "BATTERY_CREATED", "BATTERY", batteryId, "systemTraceCode=" + traceCode, request);
        return requireBattery(currentUser, batteryId, request);
    }

    private Candidate candidateForUpdate(CurrentUser currentUser, Long candidateId, HttpServletRequest request) {
        List<Candidate> candidates = jdbcTemplate.query("""
                SELECT id, enterprise_id, original_code, battery_type, battery_model, manufacturer,
                       battery_chemistry, nominal_capacity, production_date, candidate_status
                FROM battery_registration_candidate
                WHERE id = ? AND enterprise_id = ?
                FOR UPDATE
                """, this::mapCandidate, candidateId, currentUser.enterpriseId());
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        if (exists("battery_registration_candidate", candidateId)) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "CANDIDATE_CROSS_ENTERPRISE_DENIED", "BATTERY_REGISTRATION_CANDIDATE", candidateId, "FORBIDDEN", "拒绝跨企业候选访问", request);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的重复候选");
        }
        throw ApiException.notFound("重复编码候选不存在");
    }

    private void closeCandidate(Long candidateId, String status) {
        int updated = jdbcTemplate.update("""
                UPDATE battery_registration_candidate
                SET candidate_status = ?, closed_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND candidate_status = 'PENDING_REVIEW'
                """, status, candidateId);
        if (updated != 1) {
            throw ApiException.conflict("DUPLICATE_CANDIDATE_ALREADY_CLOSED", "重复编码候选已经处理");
        }
    }

    private List<Long> matchedBatteryIds(Long enterpriseId, String originalCode) {
        return jdbcTemplate.queryForList("""
                SELECT id
                FROM battery
                WHERE enterprise_id = ? AND original_code = ?
                ORDER BY id
                """, Long.class, enterpriseId, originalCode);
    }

    private void validateCreateRequest(BatteryCreateRequest request) {
        if (!"PACK".equals(request.batteryType())) {
            throw ApiException.badRequest("VALIDATION_FAILED", "第一切片仅支持电池包 PACK");
        }
        if (normalizeBlank(request.batteryChemistry()) == null) {
            throw ApiException.badRequest("VALIDATION_FAILED", "电池体系不能为空，可选择 UNKNOWN");
        }
    }

    private boolean acquireLock(String lockName) {
        Integer lock = jdbcTemplate.queryForObject("SELECT GET_LOCK(?, 5)", Integer.class, lockName);
        if (lock == null || lock != 1) {
            throw ApiException.conflict("CONCURRENT_OPERATION_CONFLICT", "原始编码正在并发登记，请稍后重试");
        }
        return true;
    }

    private void releaseLock(String lockName) {
        jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
    }

    private boolean registerLockRelease(String lockName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                releaseLock(lockName);
            }
        });
        return true;
    }

    private String originalCodeLockName(Long enterpriseId, String originalCode) {
        String hash = idempotencyService.sha256Hex(enterpriseId + ":" + originalCode);
        return "battery-original:" + enterpriseId + ":" + hash.substring(0, 32);
    }

    private boolean exists(String tableName, Long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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

    private Candidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new Candidate(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getString("original_code"),
                rs.getString("battery_type"),
                rs.getString("battery_model"),
                rs.getString("manufacturer"),
                rs.getString("battery_chemistry"),
                rs.getBigDecimal("nominal_capacity"),
                rs.getObject("production_date", LocalDate.class),
                rs.getString("candidate_status"));
    }

    private record Candidate(Long id, Long enterpriseId, String originalCode, String batteryType, String batteryModel, String manufacturer, String batteryChemistry, BigDecimal nominalCapacity, LocalDate productionDate, String candidateStatus) {
    }
}
