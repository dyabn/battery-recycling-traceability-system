package com.batteryrecycling.traceability.common.exception;

import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.common.api.ApiErrorResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public GlobalExceptionHandler(AuditService auditService, CurrentUserService currentUserService) {
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return build(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ApiErrorResponse> handleValidation(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleDenied(AccessDeniedException exception, HttpServletRequest request) {
        currentUserService.currentUser().ifPresent(user -> auditService.recordRejected(
                user.enterpriseId(),
                user.id(),
                request.getMethod() + " " + request.getRequestURI(),
                "HTTP_REQUEST",
                null,
                "FORBIDDEN",
                "没有执行该操作的权限",
                request
        ));
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有执行该操作的权限", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnhandled(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "系统内部错误", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, traceId, OffsetDateTime.now(), null));
    }
}
