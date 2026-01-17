package com.sellsync.api.domain.mapping.enums;

/**
 * 상품 매핑 상태
 */
public enum MappingStatus {
    /**
     * 미매핑 - ERP 품목코드가 연결되지 않은 상태
     */
    UNMAPPED,
    
    /**
     * 추천됨 - 자동 매칭으로 추천되었으나 확정되지 않은 상태
     */
    SUGGESTED,
    
    /**
     * 매핑완료 - ERP 품목코드가 확정된 상태
     */
    MAPPED
}
