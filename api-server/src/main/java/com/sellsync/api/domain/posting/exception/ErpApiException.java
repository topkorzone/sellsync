package com.sellsync.api.domain.posting.exception;

/**
 * ERP API 호출 실패 시 발생하는 예외
 */
public class ErpApiException extends RuntimeException {

    private final String erpCode;
    private final String errorCode;
    private final Integer httpStatus;
    private final boolean retryable;

    public ErpApiException(String erpCode, String errorCode, String message) {
        this(erpCode, errorCode, message, null, true);
    }

    public ErpApiException(String erpCode, String errorCode, String message, 
                            Integer httpStatus, boolean retryable) {
        super(String.format("[%s] %s: %s", erpCode, errorCode, message));
        this.erpCode = erpCode;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public ErpApiException(String erpCode, String errorCode, String message, Throwable cause) {
        super(String.format("[%s] %s: %s", erpCode, errorCode, message), cause);
        this.erpCode = erpCode;
        this.errorCode = errorCode;
        this.httpStatus = null;
        this.retryable = true;
    }

    public String getErpCode() {
        return erpCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    // ========== Factory Methods ==========

    /**
     * 네트워크 오류 (재시도 가능)
     */
    public static ErpApiException networkError(String erpCode, Throwable cause) {
        return new ErpApiException(erpCode, "NETWORK_ERROR", 
            "Network connection failed", cause);
    }

    /**
     * 타임아웃 오류 (재시도 가능)
     */
    public static ErpApiException timeout(String erpCode) {
        return new ErpApiException(erpCode, "TIMEOUT", 
            "Request timeout", null, true);
    }

    /**
     * 인증 실패 (재시도 불가)
     */
    public static ErpApiException authenticationFailed(String erpCode) {
        return new ErpApiException(erpCode, "AUTH_FAILED", 
            "Authentication failed", 401, false);
    }

    /**
     * 서버 오류 (재시도 가능)
     */
    public static ErpApiException serverError(String erpCode, int httpStatus) {
        return new ErpApiException(erpCode, "SERVER_ERROR", 
            "ERP server error", httpStatus, true);
    }

    /**
     * 데이터 검증 실패 (재시도 불가)
     */
    public static ErpApiException validationFailed(String erpCode, String message) {
        return new ErpApiException(erpCode, "VALIDATION_ERROR", 
            message, 400, false);
    }
}
