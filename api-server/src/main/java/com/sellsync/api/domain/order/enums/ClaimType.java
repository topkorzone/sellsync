package com.sellsync.api.domain.order.enums;

/**
 * 클레임 유형
 */
public enum ClaimType {
    CANCEL("취소"),
    RETURN("반품"),
    EXCHANGE("교환");

    private final String displayName;

    ClaimType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
