package com.sellsync.api.domain.subscription.enums;

public enum SubscriptionStatus {
    TRIAL,      // 무료 체험
    ACTIVE,     // 활성 구독
    PAST_DUE,   // 결제 연체
    CANCELED,   // 해지
    SUSPENDED   // 정지
}
