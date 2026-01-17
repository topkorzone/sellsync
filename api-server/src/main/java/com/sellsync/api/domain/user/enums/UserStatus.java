package com.sellsync.api.domain.user.enums;

/**
 * 사용자 상태 열거형
 */
public enum UserStatus {
    /**
     * 활성 - 정상적으로 사용 가능
     */
    ACTIVE,
    
    /**
     * 비활성 - 일시적으로 사용 불가
     */
    INACTIVE,
    
    /**
     * 정지 - 관리자에 의해 사용 정지
     */
    SUSPENDED
}
