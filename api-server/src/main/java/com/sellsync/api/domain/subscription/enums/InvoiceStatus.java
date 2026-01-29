package com.sellsync.api.domain.subscription.enums;

public enum InvoiceStatus {
    PENDING,   // 결제 대기
    PAID,      // 결제 완료
    FAILED,    // 결제 실패
    REFUNDED   // 환불
}
