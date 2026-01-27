package com.sellsync.infra.erp.ecount.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.infra.erp.ecount.dto.EcountCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class EcountSessionService {

    private static final String ZONE_URL = "https://oapi.ecount.com/OAPI/V2/Zone";
    private static final long SESSION_TTL_SECONDS = 23 * 60 * 60; // 23시간 (24시간 - 여유)

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 테넌트별 세션 캐시: tenantId -> SessionInfo
    private final Map<UUID, SessionInfo> sessionCache = new ConcurrentHashMap<>();

    /**
     * 유효한 세션 ID 반환 (캐시 또는 신규 발급)
     */
    public String getSessionId(UUID tenantId, EcountCredentials credentials) {
        SessionInfo cached = sessionCache.get(tenantId);
        
        if (cached != null && !cached.isExpired()) {
            return cached.sessionId;
        }
        
        // 새 세션 발급
        String sessionId = login(credentials);
        sessionCache.put(tenantId, new SessionInfo(sessionId, Instant.now().plusSeconds(SESSION_TTL_SECONDS)));
        
        return sessionId;
    }

    /**
     * 세션 무효화 (로그아웃 또는 에러 시)
     */
    public void invalidateSession(UUID tenantId) {
        sessionCache.remove(tenantId);
        log.info("[Ecount] Session invalidated for tenant {}", tenantId);
    }

    /**
     * Zone 조회 (Apache HttpClient 직접 사용)
     */
    public String getZone(String comCode) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(ZONE_URL);
            
            // 헤더 설정
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            // gzip, deflate만 요청 (Apache HttpClient가 자동으로 압축 해제함)
            // Brotli(br)는 별도 라이브러리가 필요하므로 제외
            httpPost.setHeader("Accept-Encoding", "gzip, deflate");
            
            // Body 설정
            String jsonBody = String.format("{\"COM_CODE\":\"%s\"}", comCode.trim());
            StringEntity entity = new StringEntity(jsonBody, ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);
            
            log.info("[Ecount] Zone API Request: URL={}, Body={}", ZONE_URL, jsonBody);
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                
                log.info("[Ecount] Zone API Response: Status={}, Body={}", 
                        response.getCode(), responseBody);
                
                JsonNode root = objectMapper.readTree(responseBody);
                
                if (root.path("Status").asInt() == 200) {
                    JsonNode data = root.path("Data");
                    
                    // EMPTY_ZONE 먼저 체크
                    if (data.path("EMPTY_ZONE").asBoolean(false)) {
                        log.error("[Ecount] Zone not assigned for comCode={}", comCode);
                        throw new EcountApiException("Zone not assigned for company code: " + comCode);
                    }
                    
                    // Data가 객체인 경우 처리
                    // DOMAIN_ZONE과 ZONE 모두 체크 (DOMAIN_ZONE 우선)
                    String zone = null;
                    if (data.isObject()) {
                        // 1. DOMAIN_ZONE 먼저 확인 (빨간색으로 강조된 필드)
                        if (!data.path("DOMAIN_ZONE").isMissingNode()) {
                            zone = data.path("DOMAIN_ZONE").asText();
                            log.info("[Ecount] Found DOMAIN_ZONE: {}", zone);
                        }
                        
                        // 2. DOMAIN_ZONE이 없으면 ZONE 확인
                        if ((zone == null || zone.isEmpty() || "null".equals(zone)) 
                                && !data.path("ZONE").isMissingNode()) {
                            zone = data.path("ZONE").asText();
                            log.info("[Ecount] Found ZONE: {}", zone);
                        }
                        
                        // 유효한 zone 값 확인
                        if (zone != null && !zone.isEmpty() && !"null".equals(zone)) {
                            log.info("[Ecount] Zone lookup success: comCode={}, zone={}", comCode, zone);
                            return zone;
                        }
                    }
                    
                    log.warn("[Ecount] Zone lookup returned no zone data: {}", responseBody);
                }
                
                String errorMsg = root.path("Error").path("Message").asText("Unknown error");
                log.error("[Ecount] Zone lookup failed: status={}, error={}, response={}", 
                        root.path("Status").asText(), errorMsg, responseBody);
                throw new EcountApiException("Zone lookup failed: " + errorMsg);
            }
            
        } catch (EcountApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Ecount] Zone lookup failed for {}", comCode, e);
            throw new EcountApiException("Zone lookup failed", e);
        }
    }

    /**
     * 로그인 및 세션 발급
     */
    private String login(EcountCredentials credentials) {
        // Zone 조회 (캐시되어 있으면 사용)
        String zone = credentials.getZone();
        if (zone == null || zone.isEmpty()) {
            zone = getZone(credentials.getComCode());
            credentials.setZone(zone);
        }

        // Zone을 포함한 전체 URL 생성
        // 예: zone="AC" -> "https://oapiAC.ecount.com/OAPI/V2/OAPILogin"
        String loginUrl = String.format("https://oapi%s.ecount.com/OAPI/V2/OAPILogin", zone);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Brotli(br) 압축 제외 - Ecount API 서버가 br을 제대로 처리하지 못할 수 있음
        headers.set("Accept-Encoding", "gzip, deflate");

        // JSON 문자열을 직접 생성하여 Talend 성공 요청과 정확히 동일하게 구성
        String jsonBody = String.format(
            "{\"COM_CODE\":\"%s\",\"USER_ID\":\"%s\",\"API_CERT_KEY\":\"%s\",\"LAN_TYPE\":\"%s\",\"ZONE\":\"%s\"}",
            credentials.getComCode().trim(),
            credentials.getUserId().trim(),
            credentials.getApiKey().trim(),
            "ko-KR",
            zone.trim()
        );

        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        try {
            String maskedApiKey = credentials.getApiKey().substring(0, 8) + "..." + 
                                  credentials.getApiKey().substring(credentials.getApiKey().length() - 4);
            log.info("[Ecount] Login Request: URL={}, Body={}", loginUrl, jsonBody);
            log.info("[Ecount] Login Request Details: COM_CODE={}, USER_ID={}, API_CERT_KEY={}, ZONE={}", 
                    credentials.getComCode(), credentials.getUserId(), maskedApiKey, zone);
            
            ResponseEntity<String> response = restTemplate.postForEntity(loginUrl, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            
            log.info("[Ecount] Login Response: Status={}, Body={}", 
                    response.getStatusCode(), response.getBody());
            
            // Status는 숫자 200 또는 문자열 "200"일 수 있음
            int status = root.path("Status").asInt(-1);
            if (status == 200) {
                JsonNode data = root.path("Data");
                
                // 응답 구조: Data.Datas.SESSION_ID
                JsonNode datas = data.path("Datas");
                String sessionId = datas.path("SESSION_ID").asText();
                
                if (sessionId != null && !sessionId.isEmpty() && !"null".equals(sessionId)) {
                    log.info("[Ecount] Login successful for company {}, sessionId={}", 
                            credentials.getComCode(), sessionId);
                    return sessionId;
                }
                
                log.error("[Ecount] Login response missing SESSION_ID: {}", response.getBody());
                throw new EcountApiException("Login response missing SESSION_ID");
            }
            
            String errorMsg = root.path("Error").path("Message").asText("Unknown error");
            log.error("[Ecount] Login failed: status={}, error={}", status, errorMsg);
            throw new EcountApiException("Login failed: " + errorMsg);
            
        } catch (EcountApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Ecount] Login failed for {}", credentials.getComCode(), e);
            throw new EcountApiException("Login failed", e);
        }
    }

    private record SessionInfo(String sessionId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
