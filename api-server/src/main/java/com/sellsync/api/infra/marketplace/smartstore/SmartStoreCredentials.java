package com.sellsync.api.infra.marketplace.smartstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 스마트스토어 인증 정보
 */
@Data
@Slf4j
public class SmartStoreCredentials {
    private String clientId;
    private String clientSecret;
    
    /**
     * JSON 문자열을 SmartStoreCredentials 객체로 파싱
     * 
     * @param json 인증 정보 JSON (예: {"clientId":"...", "clientSecret":"..."})
     * @return SmartStoreCredentials 객체
     * @throws IllegalArgumentException JSON 파싱 실패 또는 필수 필드 누락 시
     */
    public static SmartStoreCredentials parse(String json) {
        // null 체크
        if (json == null || json.trim().isEmpty()) {
            log.error("[SmartStore] Credentials is null or empty");
            throw new IllegalArgumentException(
                "스마트스토어 인증 정보가 설정되지 않았습니다. " +
                "설정 > 연동 관리에서 스토어 인증 정보를 등록해주세요."
            );
        }
        
        ObjectMapper mapper = new ObjectMapper();
        SmartStoreCredentials credentials;
        
        try {
            credentials = mapper.readValue(json, SmartStoreCredentials.class);
        } catch (Exception e) {
            // JSON 형식 오류 시 일부만 로깅 (보안)
            String preview = json.length() > 50 ? json.substring(0, 50) + "..." : json;
            log.error("[SmartStore] Failed to parse credentials. JSON preview: {}", preview);
            throw new IllegalArgumentException(
                "스마트스토어 인증 정보 형식이 올바르지 않습니다. " +
                "JSON 형식이어야 합니다: {\"clientId\":\"...\", \"clientSecret\":\"...\"}. " +
                "오류: " + e.getMessage(),
                e
            );
        }
        
        // 필수 필드 검증
        if (credentials.getClientId() == null || credentials.getClientId().trim().isEmpty()) {
            log.error("[SmartStore] clientId is missing in credentials");
            throw new IllegalArgumentException(
                "스마트스토어 clientId가 설정되지 않았습니다. " +
                "설정 > 연동 관리에서 올바른 인증 정보를 등록해주세요."
            );
        }
        
        if (credentials.getClientSecret() == null || credentials.getClientSecret().trim().isEmpty()) {
            log.error("[SmartStore] clientSecret is missing in credentials");
            throw new IllegalArgumentException(
                "스마트스토어 clientSecret이 설정되지 않았습니다. " +
                "설정 > 연동 관리에서 올바른 인증 정보를 등록해주세요."
            );
        }
        
        log.debug("[SmartStore] Credentials parsed successfully. clientId: {}", 
                 credentials.getClientId().substring(0, Math.min(8, credentials.getClientId().length())) + "...");
        
        return credentials;
    }
}
