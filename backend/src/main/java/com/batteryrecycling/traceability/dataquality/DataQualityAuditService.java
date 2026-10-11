package com.batteryrecycling.traceability.dataquality;

import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataQualityAuditService {
    private static final String TRACE_ID_ATTRIBUTE = "traceId";

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public DataQualityAuditService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CurrentUser user, String actionCode, String objectType, Long objectId, String result, String reason, HttpServletRequest request) {
        record(user == null ? null : user.enterpriseId(), user == null ? null : user.id(), actionCode, objectType, objectId, null, null, result, reason, request, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CurrentUser user, String actionCode, String objectType, Long objectId, String result, String reason, HttpServletRequest request, String idempotencyKeyHash) {
        record(user == null ? null : user.enterpriseId(), user == null ? null : user.id(), actionCode, objectType, objectId, null, null, result, reason, request, idempotencyKeyHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CurrentUser user, String actionCode, String objectType, Long objectId, String beforeState, String afterState, String result, String reason, HttpServletRequest request, String idempotencyKeyHash) {
        record(user == null ? null : user.enterpriseId(), user == null ? null : user.id(), actionCode, objectType, objectId, beforeState, afterState, result, reason, request, idempotencyKeyHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long enterpriseId, Long userId, String actionCode, String objectType, Long objectId, String result, String reason, HttpServletRequest request, String idempotencyKeyHash) {
        record(enterpriseId, userId, actionCode, objectType, objectId, null, null, result, reason, request, idempotencyKeyHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long enterpriseId, Long userId, String actionCode, String objectType, Long objectId, String beforeState, String afterState, String result, String reason, HttpServletRequest request, String idempotencyKeyHash) {
        insert(enterpriseId, userId, actionCode, objectType, objectId, beforeState, afterState, result, reason, request, idempotencyKeyHash);
    }

    public void recordInCurrentTransaction(CurrentUser user, String actionCode, String objectType, Long objectId, String beforeState, String afterState, String result, String reason, HttpServletRequest request, String idempotencyKeyHash) {
        insert(user == null ? null : user.enterpriseId(), user == null ? null : user.id(), actionCode, objectType, objectId, beforeState, afterState, result, reason, request, idempotencyKeyHash);
    }

    public String traceId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        Object existing = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (existing instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        return traceId;
    }

    private void insert(Long enterpriseId, Long userId, String actionCode, String objectType, Long objectId, String beforeState, String afterState, String result, String reason, HttpServletRequest request, String idempotencyKeyHash) {
        String traceId = traceId(request);
        if (reason != null && reason.length() > 500) {
            reason = reason.substring(0, 500);
        }
        jdbcTemplate.update("""
                INSERT INTO dq_operation_audit (
                  id, enterprise_id, action_code, object_type, object_id, before_state, after_state,
                  result, reason, operated_by, operated_at, trace_id, idempotency_key_hash
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                idGenerator.nextId(),
                enterpriseId,
                actionCode,
                objectType,
                objectId,
                beforeState,
                afterState,
                result,
                reason,
                userId,
                LocalDateTime.now(),
                traceId,
                idempotencyKeyHash);
    }
}
