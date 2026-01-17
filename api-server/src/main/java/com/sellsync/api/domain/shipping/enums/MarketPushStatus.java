package com.sellsync.api.domain.shipping.enums;

/**
 * 마켓 송장 푸시 상태 (T-001-3: Market Push State Machine)
 * 
 * TRD v4 기준:
 * - MARKET_PUSH_REQUESTED: 마켓 푸시 요청 (초기 상태)
 * - MARKET_PUSHED: 마켓 푸시 완료 (재실행 금지)
 * - FAILED: 마켓 푸시 실패 (재시도 가능)
 */
public enum MarketPushStatus {
    MARKET_PUSH_REQUESTED("마켓푸시요청"),
    MARKET_PUSHED("마켓푸시완료"),
    FAILED("실패");

    private final String displayName;

    MarketPushStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 상태 전이 가능 여부 검증 (State Machine)
     * 
     * 허용되는 전이:
     * - MARKET_PUSH_REQUESTED -> MARKET_PUSHED (푸시 성공)
     * - MARKET_PUSH_REQUESTED -> FAILED (푸시 실패)
     * - FAILED -> MARKET_PUSH_REQUESTED (재시도)
     * 
     * 금지되는 전이:
     * - MARKET_PUSHED -> * (재실행 금지)
     */
    public boolean canTransitionTo(MarketPushStatus target) {
        return switch (this) {
            case MARKET_PUSH_REQUESTED -> target == MARKET_PUSHED || target == FAILED;
            case FAILED -> target == MARKET_PUSH_REQUESTED; // retry
            case MARKET_PUSHED -> false; // 푸시 완료된 건은 상태 변경 불가 (재실행 금지)
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
        return this == MARKET_PUSHED;
    }

    /**
     * 재시도 대상 조회용 상태 체크
     */
    public boolean isPending() {
        return this == MARKET_PUSH_REQUESTED;
    }
}
