package com.batteryrecycling.traceability.idempotency;

import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyRecordService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public IdempotencyRecordService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    public List<IdempotencyRecord> find(CurrentUser currentUser, String operationCode, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT request_hash, process_status, response_body
                FROM idempotency_record
                WHERE enterprise_id = ? AND operator_user_id = ? AND operation_code = ? AND idempotency_key = ?
                """,
                (rs, rowNum) -> new IdempotencyRecord(rs.getString("request_hash"), rs.getString("process_status"), rs.getString("response_body")),
                currentUser.enterpriseId(), currentUser.id(), operationCode, idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createProcessing(CurrentUser currentUser, String operationCode, String idempotencyKey, String requestHash) {
        jdbcTemplate.update("""
                INSERT INTO idempotency_record (
                  id, enterprise_id, operator_user_id, operation_code, idempotency_key,
                  request_hash, process_status, created_at, updated_at, expires_at, version
                )
                VALUES (?, ?, ?, ?, ?, ?, 'PROCESSING', ?, ?, ?, 0)
                """,
                idGenerator.nextId(),
                currentUser.enterpriseId(),
                currentUser.id(),
                operationCode,
                idempotencyKey,
                requestHash,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1));
    }

    public void markSucceeded(CurrentUser currentUser, String operationCode, String idempotencyKey, String responseBody) {
        jdbcTemplate.update("""
                UPDATE idempotency_record
                SET process_status = 'SUCCEEDED', response_code = 'OK', response_body = ?, updated_at = CURRENT_TIMESTAMP(3)
                WHERE enterprise_id = ? AND operator_user_id = ? AND operation_code = ? AND idempotency_key = ?
                """,
                responseBody,
                currentUser.enterpriseId(),
                currentUser.id(),
                operationCode,
                idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(CurrentUser currentUser, String operationCode, String idempotencyKey, String responseCode) {
        jdbcTemplate.update("""
                UPDATE idempotency_record
                SET process_status = 'FAILED', response_code = ?, updated_at = CURRENT_TIMESTAMP(3)
                WHERE enterprise_id = ? AND operator_user_id = ? AND operation_code = ? AND idempotency_key = ?
                """,
                responseCode,
                currentUser.enterpriseId(),
                currentUser.id(),
                operationCode,
                idempotencyKey);
    }

    public record IdempotencyRecord(String requestHash, String status, String responseBody) {
    }
}
