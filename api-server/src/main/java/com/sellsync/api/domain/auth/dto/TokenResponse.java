package com.sellsync.api.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 토큰 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    
    /**
     * Access Token
     */
    private String accessToken;
    
    /**
     * Refresh Token
     */
    private String refreshToken;
    
    /**
     * 만료 시간 (초 단위)
     */
    private long expiresIn;
}
