package com.sellsync.api.domain.sync.exception;

import java.util.UUID;

/**
 * 동기화 작업을 찾을 수 없을 때 발생하는 예외
 */
public class SyncJobNotFoundException extends RuntimeException {

    public SyncJobNotFoundException(UUID syncJobId) {
        super(String.format("SyncJob not found: %s", syncJobId));
    }

    public SyncJobNotFoundException(String message) {
        super(message);
    }
}
