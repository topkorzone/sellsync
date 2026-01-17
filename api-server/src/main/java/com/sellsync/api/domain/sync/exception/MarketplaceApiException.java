package com.sellsync.api.domain.sync.exception;

import com.sellsync.api.domain.order.enums.Marketplace;

/**
 * 마켓플레이스 API 호출 실패 시 발생하는 예외
 */
public class MarketplaceApiException extends RuntimeException {

    private final Marketplace marketplace;
    private final String errorCode;
    private final Integer httpStatus;
    private final boolean retryable;

    public MarketplaceApiException(Marketplace marketplace, String errorCode, String message) {
        this(marketplace, errorCode, message, null, true);
    }

    public MarketplaceApiException(Marketplace marketplace, String errorCode, String message, 
                                    Integer httpStatus, boolean retryable) {
        super(String.format("[%s] %s: %s", marketplace, errorCode, message));
        this.marketplace = marketplace;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public MarketplaceApiException(Marketplace marketplace, String errorCode, String message, Throwable cause) {
        super(String.format("[%s] %s: %s", marketplace, errorCode, message), cause);
        this.marketplace = marketplace;
        this.errorCode = errorCode;
        this.httpStatus = null;
        this.retryable = true;
    }

    public Marketplace getMarketplace() {
        return marketplace;
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

    /**
     * 네트워크 오류 (재시도 가능)
     */
    public static MarketplaceApiException networkError(Marketplace marketplace, Throwable cause) {
        return new MarketplaceApiException(marketplace, "NETWORK_ERROR", 
            "Network connection failed", cause);
    }

    /**
     * 타임아웃 오류 (재시도 가능)
     */
    public static MarketplaceApiException timeout(Marketplace marketplace) {
        return new MarketplaceApiException(marketplace, "TIMEOUT", 
            "Request timeout", null, true);
    }

    /**
     * Rate Limit 초과 (재시도 가능)
     */
    public static MarketplaceApiException rateLimitExceeded(Marketplace marketplace) {
        return new MarketplaceApiException(marketplace, "RATE_LIMIT_EXCEEDED", 
            "API rate limit exceeded", 429, true);
    }

    /**
     * 인증 실패 (재시도 불가)
     */
    public static MarketplaceApiException authenticationFailed(Marketplace marketplace) {
        return new MarketplaceApiException(marketplace, "AUTH_FAILED", 
            "Authentication failed", 401, false);
    }

    /**
     * 권한 없음 (재시도 불가)
     */
    public static MarketplaceApiException forbidden(Marketplace marketplace) {
        return new MarketplaceApiException(marketplace, "FORBIDDEN", 
            "Permission denied", 403, false);
    }

    /**
     * 서버 오류 (재시도 가능)
     */
    public static MarketplaceApiException serverError(Marketplace marketplace, int httpStatus) {
        return new MarketplaceApiException(marketplace, "SERVER_ERROR", 
            "Marketplace server error", httpStatus, true);
    }
}
