package com.batteryrecycling.traceability.idempotency;

import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdempotencyService {
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final IdempotencyRecordService recordService;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyService(ObjectMapper objectMapper, CurrentUserService currentUserService, IdempotencyRecordService recordService, TransactionTemplate transactionTemplate) {
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
        this.recordService = recordService;
        this.transactionTemplate = transactionTemplate;
    }

    public <T> T execute(String operationCode, String idempotencyKey, String canonicalRequest, Class<T> responseType, Supplier<T> operation) {
        validateKey(idempotencyKey);
        String requestHash = sha256Hex(canonicalRequest);
        CurrentUser currentUser = currentUserService.requireCurrentUser();
        T cachedResponse = cachedResponse(currentUser, operationCode, idempotencyKey, requestHash, responseType);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        if (recordService.find(currentUser, operationCode, idempotencyKey).isEmpty()) {
            try {
                recordService.createProcessing(currentUser, operationCode, idempotencyKey, requestHash);
            } catch (DataIntegrityViolationException exception) {
                T concurrentCachedResponse = cachedResponse(currentUser, operationCode, idempotencyKey, requestHash, responseType);
                if (concurrentCachedResponse != null) {
                    return concurrentCachedResponse;
                }
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key 正在处理中，请稍后重试");
            }
        }

        try {
            return transactionTemplate.execute(status -> {
                T response = operation.get();
                try {
                    recordService.markSucceeded(currentUser, operationCode, idempotencyKey, objectMapper.writeValueAsString(response));
                } catch (Exception exception) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "幂等响应写入失败");
                }
                return response;
            });
        } catch (RuntimeException exception) {
            recordService.markFailed(currentUser, operationCode, idempotencyKey, exception instanceof ApiException apiException ? apiException.code() : "FAILED");
            throw exception;
        }
    }

    private <T> T cachedResponse(CurrentUser currentUser, String operationCode, String idempotencyKey, String requestHash, Class<T> responseType) {
        List<IdempotencyRecordService.IdempotencyRecord> records = recordService.find(currentUser, operationCode, idempotencyKey);
        if (records.isEmpty()) {
            return null;
        }
        IdempotencyRecordService.IdempotencyRecord record = records.get(0);
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
        if ("FAILED".equals(record.status())) {
            throw ApiException.conflict("IDEMPOTENCY_PREVIOUSLY_FAILED", "同一 Idempotency-Key 对应的首次请求已失败，请使用新的 Idempotency-Key");
        }
        throw ApiException.conflict("IDEMPOTENCY_REQUEST_PROCESSING", "Idempotency-Key 正在处理中，请稍后重试");
    }

    public String sha256Hex(String canonicalRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw ApiException.badRequest("VALIDATION_FAILED", "请求摘要计算失败");
        }
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "写操作必须携带 Idempotency-Key");
        }
        if (idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Idempotency-Key 长度必须为 8 到 128 个字符");
        }
    }
}
