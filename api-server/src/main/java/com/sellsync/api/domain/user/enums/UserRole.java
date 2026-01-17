package com.sellsync.api.domain.user.enums;

/**
 * 사용자 권한 열거형
 * 
 * <p>TRD v5 기준 권한 체계:
 * <ul>
 *   <li>SUPER_ADMIN: 플랫폼 운영자 - 모든 테넌트 접근</li>
 *   <li>TENANT_ADMIN: 고객사 관리자 - 자사 테넌트 전체 권한</li>
 *   <li>OPERATOR: 실무자 - 조회 + 재처리 권한</li>
 *   <li>VIEWER: 읽기전용 - 조회만 가능</li>
 * </ul>
 */
public enum UserRole {
    /**
     * 플랫폼 운영자 - 모든 테넌트 접근 가능
     */
    SUPER_ADMIN,
    
    /**
     * 고객사 관리자 - 자사 테넌트 전체 권한
     */
    TENANT_ADMIN,
    
    /**
     * 실무자 - 조회 및 재처리 권한
     */
    OPERATOR,
    
    /**
     * 읽기 전용 사용자 - 조회만 가능
     */
    VIEWER
}
