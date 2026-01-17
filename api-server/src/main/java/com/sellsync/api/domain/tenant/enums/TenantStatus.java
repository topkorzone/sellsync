package com.sellsync.api.domain.tenant.enums;

/**
 * 테넌트(고객사) 상태 열거형
 */
public enum TenantStatus {
    /**
     * 활성 - 정상 운영 중
     */
    ACTIVE,
    
    /**
     * 비활성 - 일시 중지
     */
    INACTIVE,
    
    /**
     * 정지 - 서비스 이용 정지
     */
    SUSPENDED,
    
    /**
     * 해지 - 계약 종료
     */
    TERMINATED
}
