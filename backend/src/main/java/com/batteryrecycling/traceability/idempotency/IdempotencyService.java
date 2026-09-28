package com.batteryrecycling.traceability.idempotency;

import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;

    public IdempotencyService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, ObjectMapper objectMapper, CurrentUserService currentUserService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public <T> T execute(String operationCode, String idempotencyKey, String requestHash, Class<T> responseType, Supplier<T> operation) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "写操作必须携带 Idempotency-Key");
        }
        CurrentUser currentUser = currentUserService.requireCurrentUser();
        List<Record> records = jdbcTemplate.query("""
                SELECT request_hash, process_status, response_body
                FROM idempotency_record
                WHERE enterprise_id = ? AND operator_user_id = ? AND operation_code = ? AND idempotency_key = ?
                """,
                (rs, rowNum) -> new Record(rs.getString("request_hash"), rs.getString("process_status"), rs.getString("response_body")),
                currentUser.enterpriseId(), currentUser.id(), operationCode, idempotencyKey);

        if (!records.isEmpty()) {
            Record record = records.get(0);
            if (!record.requestHash().equals(requestHash)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同请求");
            }
            if ("SUCCEEDED".equals(record.status()) && record.responseBody() != null) {
                try {
                    return objectMapper.readValue(record.responseBody(), responseType);
                } catch (Exception exception) {
                    throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "幂等响应读取失败，请使用新的 Idempotency-Key");
                }
            }
        } else {
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
                    LocalDateTime.now().plusDays(1)
            );
        }

        try {
            T response = operation.get();
            jdbcTemplate.update("""
                    UPDATE idempotency_record
                    SET process_status = 'SUCCEEDED', response_code = 'OK', response_body = ?, updated_at = CURRENT_TIMESTAMP(3)
                    WHERE enterprise_id = ? AND operator_user_id = ? AND operation_code = ? AND idempotency_key = ?
                    """,
                    objectMapper.writeValueAsString(response),
                    currentUser.enterpriseId(),
                    currentUser.id(),
                    operationCode,
                    idempotencyKey
            );
            return response;
        } catch (RuntimeException exception) {
            markFailed(operationCode, idempotencyKey, currentUser);
            throw exception;
        } catch (Exception exception) {
            markFailed(operationCode, idempotencyKey, currentUser);
            throw ApiException.badRequest("VALIDATION_FAILED", "幂等响应写入失败");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String operationCode, String idempotencyKey, CurrentUser currentUser) {
        jdbcTemplate.update("""
                UPDATE idempotency_record
                SET process_status = 'FAILED', response_code = 'FAILED', updated_at = CURRENT_TIMESTAMP(3)
                WHERE enterprise_id = ? AND operator_user_id = ? AND operation_code = ? AND idempotency_key = ?
                """, currentUser.enterpriseId(), currentUser.id(), operationCode, idempotencyKey);
    }

    private record Record(String requestHash, String status, String responseBody) {
    }
}
