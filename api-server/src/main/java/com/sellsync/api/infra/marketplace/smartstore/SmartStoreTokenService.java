package com.sellsync.api.infra.marketplace.smartstore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 스마트스토어 OAuth 토큰 관리 서비스
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SmartStoreTokenService {

    private static final String TOKEN_URL = "https://api.commerce.naver.com/external/v1/oauth2/token";
    
    private final RestTemplate restTemplate;
    
    // 토큰 캐시 (clientId -> TokenInfo)
    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

    /**
     * Access Token 조회 (캐시 있으면 재사용, 없으면 새로 발급)
     */
    public String getAccessToken(SmartStoreCredentials credentials) {
        String cacheKey = credentials.getClientId();
        TokenInfo cached = tokenCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            log.debug("[SmartStore] Using cached token for clientId: {}", cacheKey);
            return cached.accessToken;
        }
        
        // 새 토큰 발급
        String newToken = requestNewToken(credentials);
        tokenCache.put(cacheKey, new TokenInfo(newToken, Instant.now().plusSeconds(3500))); // 1시간 - 여유분
        log.info("[SmartStore] New token issued for clientId: {}", cacheKey);
        
        return newToken;
    }

    /**
     * OAuth 토큰 발급 요청
     * 
     * 스마트스토어 API 인증 방식:
     * 1. timestamp = 현재시간(밀리초) - 3000
     * 2. password = {CLIENT_ID}_{timestamp}
     * 3. bcrypt 해싱: BCrypt.hashpw(password, CLIENT_SECRET)
     * 4. Base64 인코딩
     * 5. 요청 바디에 client_id, timestamp, client_secret_sign, grant_type, type 포함
     */
    private String requestNewToken(SmartStoreCredentials credentials) {
        try {
            // 1. 타임스탬프 생성 (현재 시간 - 3초)
            long timestamp = System.currentTimeMillis() - 3000;
            String timestampStr = String.valueOf(timestamp);
            
            log.debug("[SmartStore] Timestamp: {}", timestampStr);
            
            // 2. password 생성: {CLIENT_ID}_{timestamp}
            String password = credentials.getClientId() + "_" + timestampStr;
            
            // 3. bcrypt 해싱 (CLIENT_SECRET을 salt로 사용)
            // ⚠️ 네이버 스마트스토어의 CLIENT_SECRET은 불완전한 BCrypt salt 형식 (26자)
            // Spring Security BCrypt는 최소 29자 필요 → 패딩 처리
            String salt = normalizeBcryptSalt(credentials.getClientSecret());
            String hashed = BCrypt.hashpw(password, salt);
            log.debug("[SmartStore] Hashed password (first 50 chars): {}...", 
                    hashed.substring(0, Math.min(50, hashed.length())));
            
            // 4. Base64 인코딩
            String clientSecretSign = Base64.getEncoder()
                    .encodeToString(hashed.getBytes(StandardCharsets.UTF_8));
            log.debug("[SmartStore] Client secret sign (first 50 chars): {}...", 
                    clientSecretSign.substring(0, Math.min(50, clientSecretSign.length())));
            
            // 5. 요청 바디 구성
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("client_id", credentials.getClientId());
            requestBody.add("timestamp", timestampStr);
            requestBody.add("client_secret_sign", clientSecretSign);
            requestBody.add("grant_type", "client_credentials");
            requestBody.add("type", "SELF");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

            log.debug("[SmartStore] Requesting token for clientId: {}...", 
                    credentials.getClientId().substring(0, Math.min(8, credentials.getClientId().length())));

            // 6. API 호출
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_URL, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<?, ?> responseBody = response.getBody();
                if (responseBody != null) {
                    Object accessToken = responseBody.get("access_token");
                    if (accessToken != null) {
                        log.info("[SmartStore] Token obtained successfully");
                        return accessToken.toString();
                    }
                }
            }
            
            throw new RuntimeException("Failed to get SmartStore token: " + response.getStatusCode());
            
        } catch (Exception e) {
            log.error("[SmartStore] Token request failed: {}", e.getMessage(), e);
            throw new RuntimeException("SmartStore authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * BCrypt salt 정규화
     * 
     * 네이버 스마트스토어의 CLIENT_SECRET은 불완전한 BCrypt salt 형식 (26자)
     * Spring Security BCrypt는 완전한 형식 (29자) 필요
     * 
     * 형식: $2a$[rounds]$[22-character-salt]
     * 예: $2a$04$XdHDByvGUqJD5rn5Pjm7Re (26자) → $2a$04$XdHDByvGUqJD5rn5Pjm7Reu (29자)
     * 
     * @param clientSecret 원본 CLIENT_SECRET
     * @return 정규화된 BCrypt salt (29자)
     */
    private String normalizeBcryptSalt(String clientSecret) {
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalArgumentException("CLIENT_SECRET is null or empty");
        }
        
        // BCrypt salt 형식 검증: $2[a/b/y]$[rounds]$[salt]
        if (!clientSecret.startsWith("$2a$") && 
            !clientSecret.startsWith("$2b$") && 
            !clientSecret.startsWith("$2y$")) {
            log.warn("[SmartStore] CLIENT_SECRET이 올바른 BCrypt 형식이 아닙니다: {}", 
                    clientSecret.substring(0, Math.min(10, clientSecret.length())));
        }
        
        // 이미 완전한 형식이면 그대로 반환
        if (clientSecret.length() >= 29) {
            log.debug("[SmartStore] CLIENT_SECRET이 이미 완전한 형식입니다 ({}자)", clientSecret.length());
            return clientSecret;
        }
        
        // 부족한 문자를 패딩 (BCrypt base64 알파벳 사용: ./A-Za-z0-9)
        // 네이버는 항상 같은 형식을 사용하므로 'u' 문자로 패딩
        int paddingNeeded = 29 - clientSecret.length();
        String padding = "u".repeat(paddingNeeded);
        String normalizedSalt = clientSecret + padding;
        
        log.debug("[SmartStore] CLIENT_SECRET 정규화: {}자 → {}자 (패딩: {} 문자)", 
                clientSecret.length(), normalizedSalt.length(), paddingNeeded);
        
        return normalizedSalt;
    }

    /**
     * 토큰 정보 (만료 시간 포함)
     */
    private record TokenInfo(String accessToken, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
