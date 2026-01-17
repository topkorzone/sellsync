package com.sellsync.api.domain.shipping.exception;

/**
 * 송장 발급 상태 전이 불가 예외 (ADR-0001 State Machine 위반)
 */
public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
