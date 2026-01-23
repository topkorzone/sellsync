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
        
        // ✅ 공백 제거 (DB에 공백이 포함되어 있을 수 있음)
        credentials.setClientId(credentials.getClientId().trim());
        credentials.setClientSecret(credentials.getClientSecret().trim());
        
        // ✅ 디버깅: clientSecret 길이 확인 (BCrypt salt는 정확히 29자 필요)
        int clientSecretLength = credentials.getClientSecret().length();
        log.info("[SmartStore] Credentials parsed - clientId: {}..., clientSecret length: {}, starts with: {}", 
                 credentials.getClientId().substring(0, Math.min(8, credentials.getClientId().length())),
                 clientSecretLength,
                 credentials.getClientSecret().substring(0, Math.min(7, credentials.getClientSecret().length())));
        
        if (clientSecretLength != 29) {
            log.warn("[SmartStore] ⚠️ CLIENT_SECRET 길이가 예상과 다릅니다! (현재: {} 자, 권장: 29자)", clientSecretLength);
            if (clientSecretLength < 29) {
                log.error("[SmartStore] ❌ CLIENT_SECRET이 너무 짧습니다!");
                log.error("[SmartStore] BCrypt salt 형식이 아닌 것으로 보입니다. 전체 값: {}", credentials.getClientSecret());
                log.error("[SmartStore] 해결: 네이버 커머스 개발자 센터에서 올바른 client_secret을 확인하세요.");
            }
        }
        
        return credentials;
    }
}
