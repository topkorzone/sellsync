package com.sellsync.api.domain.order.enums;

/**
 * 클레임 상태
 */
public enum ClaimStatus {
    REQUESTED("요청"),
    APPROVED("승인"),
    REJECTED("거부"),
    COMPLETED("완료");

    private final String displayName;

    ClaimStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
