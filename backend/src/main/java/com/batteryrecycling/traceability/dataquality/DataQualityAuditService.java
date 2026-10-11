package com.batteryrecycling.traceability.dataquality;

import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataQualityAuditService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public DataQualityAuditService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CurrentUser user, String actionCode, String objectType, Long objectId, String result, String reason, HttpServletRequest request) {
        record(user == null ? null : user.enterpriseId(), user == null ? null : user.id(), actionCode, objectType, objectId, result, reason, request, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CurrentUser user, String actionCode, String objectType, Long objectId, String result, String reason, HttpServletRequest request, String idempotencyKeyHash) {
        record(user == null ? null : user.enterpriseId(), user == null ? null : user.id(), actionCode, objectType, objectId, result, reason, request, idempotencyKeyHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long enterpriseId, Long userId, String actionCode, String objectType, Long objectId, String result, String reason, HttpServletRequest request, String idempotencyKeyHash) {
        String traceId = request == null ? null : request.getHeader("X-Trace-Id");
        if (reason != null && reason.length() > 500) {
            reason = reason.substring(0, 500);
        }
        jdbcTemplate.update("""
                INSERT INTO dq_operation_audit (
                  id, enterprise_id, action_code, object_type, object_id, before_state, after_state,
                  result, reason, operated_by, operated_at, trace_id, idempotency_key_hash
                )
                VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?, ?)
                """,
                idGenerator.nextId(),
                enterpriseId,
                actionCode,
                objectType,
                objectId,
                result,
                reason,
                userId,
                LocalDateTime.now(),
                traceId,
                idempotencyKeyHash);
    }
}
