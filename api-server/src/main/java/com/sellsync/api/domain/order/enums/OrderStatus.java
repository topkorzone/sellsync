package com.sellsync.api.domain.order.enums;

/**
 * 주문 상태 (TRD v2.1)
 */
public enum OrderStatus {
    NEW("신규주문"),
    CONFIRMED("주문확인"),
    PAID("결제완료"),  // 추가: 프론트엔드 호환성
    PREPARING("상품준비중"),
    SHIPPING("배송중"),
    DELIVERED("배송완료"),
    CANCELED("취소"),
    PARTIAL_CANCELED("부분취소"),
    RETURN_REQUESTED("반품요청"),
    RETURNED("반품완료"),
    EXCHANGE_REQUESTED("교환요청"),
    EXCHANGED("교환완료");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
