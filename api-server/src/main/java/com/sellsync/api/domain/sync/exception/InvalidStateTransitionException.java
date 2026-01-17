package com.sellsync.api.domain.sync.exception;

import com.sellsync.api.domain.sync.enums.SyncJobStatus;

import java.util.UUID;

/**
 * 허용되지 않은 상태 전이 시도 시 발생하는 예외 (ADR-0001: State Machine)
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(UUID syncJobId, SyncJobStatus from, SyncJobStatus to) {
        super(String.format("Invalid state transition for SyncJob %s: %s -> %s", syncJobId, from, to));
    }

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
