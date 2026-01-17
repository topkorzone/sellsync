package com.sellsync.api.domain.shipping.enums;

/**
 * 송장 발급 상태 (ADR-0001: Shipping State Machine)
 * 
 * TRD v4 기준:
 * - INVOICE_REQUESTED: 송장 발급 요청 (초기 상태)
 * - INVOICE_ISSUED: 송장 발급 완료 (tracking_no 확보)
 * - FAILED: 송장 발급 실패 (재시도 가능)
 */
public enum ShipmentLabelStatus {
    INVOICE_REQUESTED("송장발급요청"),
    INVOICE_ISSUED("송장발급완료"),
    FAILED("실패");

    private final String displayName;

    ShipmentLabelStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 상태 전이 가능 여부 검증 (ADR-0001 State Machine)
     * 
     * 허용되는 전이:
     * - INVOICE_REQUESTED -> INVOICE_ISSUED (발급 성공)
     * - INVOICE_REQUESTED -> FAILED (발급 실패)
     * - FAILED -> INVOICE_REQUESTED (재시도)
     * 
     * 금지되는 전이:
     * - INVOICE_ISSUED -> INVOICE_REQUESTED (재발급 금지)
     * - INVOICE_ISSUED -> FAILED (이미 발급 완료된 송장은 실패로 변경 불가)
     */
    public boolean canTransitionTo(ShipmentLabelStatus target) {
        return switch (this) {
            case INVOICE_REQUESTED -> target == INVOICE_ISSUED || target == FAILED;
            case FAILED -> target == INVOICE_REQUESTED; // retry
            case INVOICE_ISSUED -> false; // 발급 완료된 송장은 상태 변경 불가 (재발급 금지)
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
        return this == INVOICE_ISSUED;
    }

    /**
     * tracking_no가 존재해야 하는 상태인지 검증
     */
    public boolean requiresTrackingNo() {
        return this == INVOICE_ISSUED;
    }
}
