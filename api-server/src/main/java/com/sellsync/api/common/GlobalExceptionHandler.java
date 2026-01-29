package com.sellsync.api.common;

import com.sellsync.api.domain.mapping.exception.ProductMappingNotFoundException;
import com.sellsync.api.domain.mapping.exception.ProductMappingRequiredException;
import com.sellsync.api.domain.order.exception.OrderNotFoundException;
import com.sellsync.api.domain.posting.exception.ErpApiException;
import com.sellsync.api.domain.posting.exception.PostingNotFoundException;
import com.sellsync.api.domain.settlement.exception.InvalidSettlementStateException;
import com.sellsync.api.domain.settlement.exception.SettlementBatchNotFoundException;
import com.sellsync.api.domain.shipping.exception.DuplicateTrackingNoException;
import com.sellsync.api.domain.shipping.exception.MarketPushAlreadyCompletedException;
import com.sellsync.api.domain.sync.exception.MarketplaceApiException;
import com.sellsync.api.domain.sync.exception.SyncJobNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러
 *
 * <p>애플리케이션 전역에서 발생하는 예외를 일관된 형식으로 응답합니다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ============================================================
    // 400 Bad Request
    // ============================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("유효하지 않은 요청입니다");
        log.warn("Validation error: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("요청 파라미터가 유효하지 않습니다");
        log.warn("Constraint violation: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleJsonParseError(HttpMessageNotReadableException e) {
        String errorMessage = e.getMessage();
        String userMessage = "잘못된 요청 형식입니다";

        if (errorMessage != null && errorMessage.contains("UUID")) {
            if (errorMessage.contains("\"all\"")) {
                userMessage = "storeId에 'all'을 사용할 수 없습니다. " +
                             "전체 스토어를 동기화하려면 POST /api/sync/jobs/all 엔드포인트를 사용하거나, " +
                             "특정 스토어를 동기화하려면 유효한 UUID를 제공하세요.";
            } else {
                userMessage = "storeId 형식이 올바르지 않습니다. UUID 형식이어야 합니다 (예: 550e8400-e29b-41d4-a716-446655440000)";
            }
        } else if (errorMessage != null && errorMessage.contains("LocalDateTime")) {
            userMessage = "날짜/시간 형식이 올바르지 않습니다. ISO-8601 형식을 사용하세요 (예: 2024-01-14T15:30:00)";
        }

        log.warn("JSON parse error: {}", errorMessage);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("JSON_PARSE_ERROR", userMessage));
    }

    // ============================================================
    // 403 Forbidden
    // ============================================================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "접근 권한이 없습니다"));
    }

    // ============================================================
    // 404 Not Found
    // ============================================================

    @ExceptionHandler({
            OrderNotFoundException.class,
            PostingNotFoundException.class,
            SettlementBatchNotFoundException.class,
            ProductMappingNotFoundException.class,
            SyncJobNotFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
    }

    // ============================================================
    // 409 Conflict (상태 전이 오류, 중복)
    // ============================================================

    @ExceptionHandler({
            com.sellsync.api.domain.posting.exception.InvalidStateTransitionException.class,
            com.sellsync.api.domain.shipping.exception.InvalidStateTransitionException.class,
            com.sellsync.api.domain.sync.exception.InvalidStateTransitionException.class,
            InvalidSettlementStateException.class,
            DuplicateTrackingNoException.class,
            MarketPushAlreadyCompletedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleConflict(RuntimeException e) {
        log.warn("Conflict: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONFLICT", e.getMessage()));
    }

    // ============================================================
    // 422 Unprocessable Entity (비즈니스 규칙 위반)
    // ============================================================

    @ExceptionHandler(ProductMappingRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleMappingRequired(ProductMappingRequiredException e) {
        log.warn("Product mapping required: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error("MAPPING_INCOMPLETE", e.getMessage()));
    }

    // ============================================================
    // 502 Bad Gateway (외부 API 오류)
    // ============================================================

    @ExceptionHandler(ErpApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleErpApiError(ErpApiException e) {
        log.error("ERP API error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("ERP_API_ERROR", e.getMessage()));
    }

    @ExceptionHandler(MarketplaceApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleMarketplaceApiError(MarketplaceApiException e) {
        log.error("Marketplace API error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("MARKETPLACE_API_ERROR", e.getMessage()));
    }

    // ============================================================
    // 500 Internal Server Error (폴백)
    // ============================================================

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage(), e);
        if (e.getCause() != null) {
            log.error("Caused by: {} - {}",
                    e.getCause().getClass().getSimpleName(),
                    e.getCause().getMessage());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        log.error("Unexpected error: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
        if (e.getCause() != null) {
            log.error("Caused by: {} - {}",
                    e.getCause().getClass().getSimpleName(),
                    e.getCause().getMessage());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }
}
