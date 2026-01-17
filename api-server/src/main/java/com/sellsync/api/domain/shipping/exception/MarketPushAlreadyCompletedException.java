package com.sellsync.api.domain.shipping.exception;

/**
 * 이미 마켓 푸시가 완료된 경우 발생하는 예외
 * (재실행 방지 - T-001-3)
 */
public class MarketPushAlreadyCompletedException extends RuntimeException {
    public MarketPushAlreadyCompletedException(String message) {
        super(message);
    }
}
