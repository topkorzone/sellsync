package com.sellsync.api.domain.settlement.enums;

/**
 * 정산 배치 상태 (ADR-0001: Settlement State Machine)
 * 
 * 상태 전이:
 * COLLECTED (마켓 정산 수집)
 * → VALIDATED (주문/금액 검증 완료)
 * → CLOSED (정산 완료)
 * 
 * 실패 시:
 * → FAILED (재시도 가능)
 * 
 * 전표 생성 여부는 SettlementBatch의 commissionPostingId, receiptPostingId로 판단
 */
public enum SettlementStatus {
    COLLECTED("수집완료"),
    VALIDATED("검증완료"),
    CLOSED("정산완료"),
    FAILED("실패");

    private final String displayName;

    SettlementStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 상태 전이 가능 여부 검증 (ADR-0001 State Machine)
     */
    public boolean canTransitionTo(SettlementStatus target) {
        return switch (this) {
            case COLLECTED -> target == VALIDATED || target == FAILED;
            case VALIDATED -> target == CLOSED || target == FAILED;
            case FAILED -> target == COLLECTED; // retry from COLLECTED
            case CLOSED -> false; // 완료된 정산은 수정 불가
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
        return this == CLOSED;
    }
}
