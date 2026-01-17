package com.sellsync.api.domain.order.enums;

/**
 * 주문 상품 상태
 */
public enum ItemStatus {
    NORMAL("정상"),
    CANCELED("취소"),
    RETURNED("반품"),
    EXCHANGED("교환");

    private final String displayName;

    ItemStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
