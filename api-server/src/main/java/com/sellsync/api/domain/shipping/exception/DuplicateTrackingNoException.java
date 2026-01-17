package com.sellsync.api.domain.shipping.exception;

/**
 * 이미 tracking_no가 존재하여 재발급 불가 예외
 */
public class DuplicateTrackingNoException extends RuntimeException {
    public DuplicateTrackingNoException(String message) {
        super(message);
    }
}
