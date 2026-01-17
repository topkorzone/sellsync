package com.sellsync.api.domain.shipment.enums;

public enum ShipmentStatus {
    CREATED,            // 생성됨
    INVOICE_CREATED,    // 송장번호 등록됨
    MARKET_PUSHED,      // 마켓 반영 완료
    SHIPPED,            // 출고/집화 완료
    DELIVERED,          // 배송 완료
    FAILED              // 실패
}
