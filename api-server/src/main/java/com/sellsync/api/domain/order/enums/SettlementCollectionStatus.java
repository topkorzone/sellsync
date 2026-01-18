package com.sellsync.api.domain.order.enums;

/**
 * 정산 수집 상태
 * 
 * 주문별 정산 데이터 수집 추적을 위한 상태
 * - NOT_COLLECTED: 정산 미수집 (기본값)
 * - COLLECTED: 정산 수집 완료 (일별정산내역 API에서 확인됨)
 * - POSTED: 전표 생성 완료
 */
public enum SettlementCollectionStatus {
    NOT_COLLECTED("정산 미수집"),
    COLLECTED("정산 수집 완료"),
    POSTED("전표 생성 완료");

    private final String displayName;

    SettlementCollectionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
