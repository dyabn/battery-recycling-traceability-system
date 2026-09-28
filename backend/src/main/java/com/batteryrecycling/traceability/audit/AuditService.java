package com.batteryrecycling.traceability.audit;

import com.batteryrecycling.traceability.common.api.IdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public AuditService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long enterpriseId,
            Long operatorUserId,
            String actionCode,
            String objectType,
            Long objectId,
            String result,
            String reason,
            HttpServletRequest request
    ) {
        insertAudit(enterpriseId, operatorUserId, actionCode, objectType, objectId, result, reason, request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(
            Long enterpriseId,
            Long operatorUserId,
            String actionCode,
            String objectType,
            Long objectId,
            String result,
            String reason,
            HttpServletRequest request
    ) {
        insertAudit(enterpriseId, operatorUserId, actionCode, objectType, objectId, result, reason, request);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSuccess(
            Long enterpriseId,
            Long operatorUserId,
            String actionCode,
            String objectType,
            Long objectId,
            String reason,
            HttpServletRequest request
    ) {
        insertAudit(enterpriseId, operatorUserId, actionCode, objectType, objectId, "SUCCESS", reason, request);
    }

    private void insertAudit(
            Long enterpriseId,
            Long operatorUserId,
            String actionCode,
            String objectType,
            Long objectId,
            String result,
            String reason,
            HttpServletRequest request
    ) {
        String ip = request == null ? null : request.getRemoteAddr();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        String traceId = request == null ? null : request.getHeader("X-Trace-Id");
        if (reason != null && reason.length() > 255) {
            reason = reason.substring(0, 255);
        }
        jdbcTemplate.update("""
                INSERT INTO audit_log (
                  id, enterprise_id, operator_user_id, action_code, object_type, object_id,
                  result, reject_reason, operated_at, ip_address, user_agent, trace_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                idGenerator.nextId(),
                enterpriseId,
                operatorUserId,
                actionCode,
                objectType,
                objectId,
                result,
                reason,
                LocalDateTime.now(),
                ip,
                userAgent,
                traceId
        );
    }
}
