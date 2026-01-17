package com.sellsync.api.domain.settlement.exception;

/**
 * 정산 상태머신 위반 시 발생하는 예외
 */
public class InvalidSettlementStateException extends RuntimeException {
    
    public InvalidSettlementStateException(String message) {
        super(message);
    }
}
