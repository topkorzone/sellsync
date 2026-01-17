package com.sellsync.api.domain.posting.enums;

/**
 * 전표 상태 (ADR-0001: Posting State Machine)
 */
public enum PostingStatus {
    READY("준비"),
    READY_TO_POST("전송준비완료"),
    POSTING_REQUESTED("전송요청"),
    POSTED("전송완료"),
    FAILED("실패");

    private final String displayName;

    PostingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 상태 전이 가능 여부 검증 (ADR-0001 State Machine)
     */
    public boolean canTransitionTo(PostingStatus target) {
        return switch (this) {
            case READY -> target == READY_TO_POST;
            case READY_TO_POST -> target == POSTING_REQUESTED;
            case POSTING_REQUESTED -> target == POSTED || target == FAILED;
            case FAILED -> target == POSTING_REQUESTED; // retry
            case POSTED -> false; // 완료된 전표는 수정 불가 (취소전표로만)
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
        return this == POSTED;
    }
}
