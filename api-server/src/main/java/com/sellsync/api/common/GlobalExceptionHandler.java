package com.sellsync.api.common;

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

    /**
     * 잘못된 인자 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", e.getMessage()));
    }

    /**
     * 접근 거부 예외 처리
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "접근 권한이 없습니다"));
    }

    /**
     * 유효성 검증 실패 예외 처리
     */
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

    /**
     * JSON 파싱 오류 처리 (HttpMessageNotReadableException)
     * 
     * <p>잘못된 JSON 형식이나 타입 변환 실패 시 발생합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleJsonParseError(HttpMessageNotReadableException e) {
        String errorMessage = e.getMessage();
        String userMessage = "잘못된 요청 형식입니다";
        
        // UUID 파싱 오류 특별 처리
        if (errorMessage != null && errorMessage.contains("UUID")) {
            if (errorMessage.contains("\"all\"")) {
                userMessage = "storeId에 'all'을 사용할 수 없습니다. " +
                             "전체 스토어를 동기화하려면 POST /api/sync/jobs/all 엔드포인트를 사용하거나, " +
                             "특정 스토어를 동기화하려면 유효한 UUID를 제공하세요.";
                log.warn("UUID parsing error: User tried to use 'all' as storeId");
            } else {
                userMessage = "storeId 형식이 올바르지 않습니다. UUID 형식이어야 합니다 (예: 550e8400-e29b-41d4-a716-446655440000)";
                log.warn("UUID parsing error: {}", errorMessage);
            }
        } else if (errorMessage != null && errorMessage.contains("LocalDateTime")) {
            userMessage = "날짜/시간 형식이 올바르지 않습니다. ISO-8601 형식을 사용하세요 (예: 2024-01-14T15:30:00)";
            log.warn("LocalDateTime parsing error: {}", errorMessage);
        } else {
            log.warn("JSON parsing error: {}", errorMessage);
        }
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("JSON_PARSE_ERROR", userMessage));
    }

    /**
     * RuntimeException 예외 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage(), e);
        
        // Caused by 정보도 로깅
        if (e.getCause() != null) {
            log.error("Caused by: {} - {}", 
                    e.getCause().getClass().getSimpleName(), 
                    e.getCause().getMessage());
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("RUNTIME_ERROR", 
                        "작업 처리 중 오류가 발생했습니다: " + e.getMessage()));
    }
    
    /**
     * 기타 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        log.error("Unexpected error: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
        
        // Caused by 정보도 로깅
        if (e.getCause() != null) {
            log.error("Caused by: {} - {}", 
                    e.getCause().getClass().getSimpleName(), 
                    e.getCause().getMessage());
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }
}
