package com.sellsync.api.domain.tenant.enums;

public enum OnboardingStatus {
    PENDING,      // 온보딩 대기 (회원가입 직후)
    IN_PROGRESS,  // 온보딩 진행 중
    COMPLETED,    // 온보딩 완료
    SKIPPED       // 온보딩 건너뛰기
}
