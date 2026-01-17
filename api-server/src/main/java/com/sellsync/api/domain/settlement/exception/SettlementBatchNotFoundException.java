package com.sellsync.api.domain.settlement.exception;

import java.util.UUID;

/**
 * 정산 배치를 찾을 수 없을 때 발생하는 예외
 */
public class SettlementBatchNotFoundException extends RuntimeException {
    
    public SettlementBatchNotFoundException(UUID settlementBatchId) {
        super(String.format("SettlementBatch not found: settlementBatchId=%s", settlementBatchId));
    }
    
    public SettlementBatchNotFoundException(String message) {
        super(message);
    }
}
