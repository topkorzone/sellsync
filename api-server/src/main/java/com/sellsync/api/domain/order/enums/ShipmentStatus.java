package com.sellsync.api.domain.order.enums;

/**
 * 송장/배송 상태 (TRD v2, ADR-0001)
 */
public enum ShipmentStatus {
    READY("배송준비"),
    INVOICE_REQUESTED("송장발급요청"),
    INVOICE_ISSUED("송장발급완료"),
    MARKET_PUSH_REQUESTED("마켓전송요청"),
    MARKET_PUSHED("마켓전송완료"),
    SHIPPED("배송중"),
    DELIVERED("배송완료"),
    FAILED("실패");

    private final String displayName;

    ShipmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 상태 전이 가능 여부 검증 (ADR-0001 State Machine)
     */
    public boolean canTransitionTo(ShipmentStatus target) {
        return switch (this) {
            case READY -> target == INVOICE_REQUESTED;
            case INVOICE_REQUESTED -> target == INVOICE_ISSUED || target == FAILED;
            case INVOICE_ISSUED -> target == MARKET_PUSH_REQUESTED;
            case MARKET_PUSH_REQUESTED -> target == MARKET_PUSHED || target == FAILED;
            case MARKET_PUSHED -> target == SHIPPED;
            case SHIPPED -> target == DELIVERED;
            case FAILED -> target == INVOICE_REQUESTED || target == MARKET_PUSH_REQUESTED;
            case DELIVERED -> false;
        };
    }
}
