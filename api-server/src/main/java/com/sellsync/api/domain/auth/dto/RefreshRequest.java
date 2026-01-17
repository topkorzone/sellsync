package com.sellsync.api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 토큰 갱신 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {
    
    @NotBlank(message = "Refresh Token은 필수입니다.")
    private String refreshToken;
}
