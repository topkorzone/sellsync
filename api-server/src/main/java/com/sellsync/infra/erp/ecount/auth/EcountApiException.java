package com.sellsync.infra.erp.ecount.auth;

public class EcountApiException extends RuntimeException {
    
    private String errorCode;
    private String errorMessage;

    public EcountApiException(String message) {
        super(message);
    }

    public EcountApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public EcountApiException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
