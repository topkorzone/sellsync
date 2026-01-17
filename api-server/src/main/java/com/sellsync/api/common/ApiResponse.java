package com.sellsync.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 표준 API 응답 DTO
 * 
 * <p>모든 API 응답은 이 형식을 따릅니다:
 * { "ok": true/false, "data": {...}, "error": {...} }
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean ok;
    private T data;
    private ErrorInfo error;

    /**
     * 에러 정보
     */
    @Data
    @Builder
    public static class ErrorInfo {
        private String code;
        private String message;
    }

    /**
     * 성공 응답 생성
     */
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .ok(true)
                .data(data)
                .build();
    }

    /**
     * 에러 응답 생성
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .ok(false)
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }
}
