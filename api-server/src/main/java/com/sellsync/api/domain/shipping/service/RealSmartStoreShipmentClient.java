package com.sellsync.api.domain.shipping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * SmartStore 송장 업데이트 실제 클라이언트 (Production)
 * 
 * 네이버 스마트스토어 API 연동:
 * - 엔드포인트: POST /v1/orders/{orderId}/shipment
 * - 인증: OAuth 2.0 + Client ID/Secret
 * - 파라미터: orderId, carrierCode, trackingNo
 * 
 * 참고:
 * - 실제 운영 환경에서는 @Primary 어노테이션으로 이 구현체를 사용
 * - 테스트 환경에서는 MockSmartStoreShipmentClient를 사용
 */
@Slf4j
@Component
@Primary  // 기본 구현체로 설정 (운영 환경)
@RequiredArgsConstructor
public class RealSmartStoreShipmentClient implements SmartStoreShipmentClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${smartstore.api.base-url:https://api.commerce.naver.com}")
    private String baseUrl;

    @Value("${smartstore.api.client-id:}")
    private String clientId;

    @Value("${smartstore.api.client-secret:}")
    private String clientSecret;

    @Value("${smartstore.api.enabled:false}")
    private boolean apiEnabled;

    @Override
    public String updateTracking(String orderId, String carrierCode, String trackingNo) throws Exception {
        // API 비활성화 시 Mock 모드로 동작
        if (!apiEnabled) {
            log.warn("[Mock 모드] SmartStore API가 비활성화되어 있습니다. (smartstore.api.enabled=false)");
            return mockResponse(orderId, carrierCode, trackingNo);
        }

        // 인증 정보 검증
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException("SmartStore API 인증 정보가 설정되지 않았습니다. " +
                "application.yml에서 smartstore.api.client-id와 client-secret을 설정하세요.");
        }

        // API 호출
        String url = String.format("%s/v1/orders/%s/shipment", baseUrl, orderId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("deliveryCompanyCode", carrierCode);
        requestBody.put("trackingNumber", trackingNo);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        log.info("[SmartStore API 호출] url={}, orderId={}, carrierCode={}, trackingNo={}", 
            url, orderId, carrierCode, trackingNo);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[SmartStore API 성공] orderId={}, status={}", orderId, response.getStatusCode());
                return response.getBody();
            } else {
                log.error("[SmartStore API 실패] orderId={}, status={}, body={}", 
                    orderId, response.getStatusCode(), response.getBody());
                throw new RuntimeException(String.format("SmartStore API 실패: status=%s, body=%s", 
                    response.getStatusCode(), response.getBody()));
            }

        } catch (Exception e) {
            log.error("[SmartStore API 오류] orderId={}, error={}", orderId, e.getMessage(), e);
            throw new RuntimeException("SmartStore API 호출 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * Mock 응답 생성 (API 비활성화 시)
     */
    private String mockResponse(String orderId, String carrierCode, String trackingNo) {
        Map<String, Object> mockData = new HashMap<>();
        mockData.put("orderId", orderId);
        mockData.put("carrierCode", carrierCode);
        mockData.put("trackingNo", trackingNo);
        mockData.put("status", "success");
        mockData.put("message", "Mock response (API disabled)");

        try {
            return objectMapper.writeValueAsString(mockData);
        } catch (Exception e) {
            return String.format("{\"orderId\":\"%s\",\"status\":\"success\"}", orderId);
        }
    }
}
