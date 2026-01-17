package com.sellsync.api.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 사용자 정보 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    
    /**
     * 사용자 ID
     */
    private UUID userId;
    
    /**
     * 테넌트 ID
     */
    private UUID tenantId;
    
    /**
     * 이메일
     */
    private String email;
    
    /**
     * 사용자명
     */
    private String username;
    
    /**
     * 권한
     */
    private String role;
    
    /**
     * 상태
     */
    private String status;
    
    /**
     * 테넌트명
     */
    private String tenantName;
}
