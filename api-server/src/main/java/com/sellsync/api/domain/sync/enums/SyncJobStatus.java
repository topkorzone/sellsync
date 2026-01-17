package com.sellsync.api.domain.sync.enums;

/**
 * 동기화 작업 상태 (ADR-0001: Sync Job State Machine)
 * 
 * 상태 전이 흐름:
 * - PENDING: 작업 대기 중 (초기 상태)
 * - RUNNING: 작업 실행 중
 * - COMPLETED: 작업 완료 (정상 종료)
 * - FAILED: 작업 실패 (재시도 가능)
 */
public enum SyncJobStatus {
    PENDING("대기"),
    RUNNING("실행중"),
    COMPLETED("완료"),
    FAILED("실패");

    private final String displayName;

    SyncJobStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 상태 전이 가능 여부 검증 (ADR-0001 State Machine)
     * 
     * 허용되는 전이:
     * - PENDING -> RUNNING (작업 시작)
     * - RUNNING -> COMPLETED (작업 성공)
     * - RUNNING -> FAILED (작업 실패)
     * - FAILED -> PENDING (재시도 준비)
     * 
     * 금지되는 전이:
     * - COMPLETED -> * (완료된 작업은 상태 변경 불가)
     * - RUNNING -> PENDING (실행 중인 작업을 대기로 되돌릴 수 없음)
     */
    public boolean canTransitionTo(SyncJobStatus target) {
        return switch (this) {
            case PENDING -> target == RUNNING;
            case RUNNING -> target == COMPLETED || target == FAILED;
            case FAILED -> target == PENDING; // retry preparation
            case COMPLETED -> false; // 완료된 작업은 수정 불가
        };
    }

    /**
     * 재시도 가능 상태 여부
     */
    public boolean isRetryable() {
        return this == FAILED;
    }

    /**
     * 완료 상태 여부
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * 실행 중 상태 여부
     */
    public boolean isRunning() {
        return this == RUNNING;
    }

    /**
     * 대기 상태 여부
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 종료 상태 여부 (COMPLETED 또는 FAILED)
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
