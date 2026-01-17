package com.sellsync.infra.erp.ecount.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
public class EcountCredentials {
    private String comCode;      // 회사코드
    private String userId;       // 사용자 ID
    private String apiKey;       // API 인증키
    private String zone;         // 존 URL (캐시용)
    
    public static EcountCredentials parse(String json) {
        try {
            return new ObjectMapper().readValue(json, EcountCredentials.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Ecount credentials", e);
        }
    }
    
    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize credentials", e);
        }
    }
}
