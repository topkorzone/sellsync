package com.sellsync.api.infra.marketplace.smartstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipment.enums.CarrierCode;
import com.sellsync.api.domain.shipping.client.MarketShipmentClient;
import com.sellsync.api.domain.shipping.dto.ShipmentPushRequest;
import com.sellsync.api.domain.shipping.dto.ShipmentPushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 네이버 스마트스토어 송장 반영 클라이언트
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SmartStoreShipmentClient implements MarketShipmentClient {

    private static final String BASE_URL = "https://api.commerce.naver.com";
    private static final String DISPATCH_PATH = "/external/v1/pay-order/seller/orders/{orderId}/product-orders/{productOrderId}/claim/dispatch";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SmartStoreTokenService tokenService;

    @Override
    public Marketplace getMarketplace() {
        return Marketplace.NAVER_SMARTSTORE;
    }

    @Override
    public ShipmentPushResult pushShipment(String credentials, ShipmentPushRequest request) {
        log.info("[SmartStore] 송장 반영 시작: order={}, product={}, tracking={}", 
                request.getMarketplaceOrderId(), 
                request.getProductOrderId(),
                request.getTrackingNo());

        try {
            // 액세스 토큰 획득
            SmartStoreCredentials creds = SmartStoreCredentials.parse(credentials);
            String accessToken = tokenService.getAccessToken(creds);

            // URL 구성
            String url = BASE_URL + DISPATCH_PATH
                    .replace("{orderId}", request.getMarketplaceOrderId())
                    .replace("{productOrderId}", request.getProductOrderId());

            // 헤더 구성
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            // 택배사 코드 변환
            CarrierCode carrier = CarrierCode.fromCode(request.getCarrierCode())
                    .orElse(CarrierCode.CJ);

            // 요청 바디 구성
            Map<String, Object> body = new HashMap<>();
            body.put("deliveryMethod", "DELIVERY");
            body.put("dispatchDate", LocalDate.now().toString());
            body.put("deliveryCompanyCode", carrier.getNaverCode());
            body.put("trackingNumber", request.getTrackingNo());

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

            // API 호출
            ResponseEntity<String> response = restTemplate.postForEntity(url, httpRequest, String.class);

            // 응답 처리
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[SmartStore] 송장 반영 성공: tracking={}", request.getTrackingNo());
                return ShipmentPushResult.success(response.getBody());
            } else {
                String errorCode = "HTTP_" + response.getStatusCode().value();
                String errorMessage = "송장 반영 실패: " + response.getStatusCode();
                log.warn("[SmartStore] 송장 반영 실패: {} - {}", errorCode, errorMessage);
                return ShipmentPushResult.failure(errorCode, errorMessage, response.getBody());
            }

        } catch (HttpClientErrorException e) {
            log.error("[SmartStore] API 오류: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            
            try {
                JsonNode errorJson = objectMapper.readTree(e.getResponseBodyAsString());
                String errorCode = errorJson.path("code").asText("UNKNOWN");
                String errorMessage = errorJson.path("message").asText(e.getMessage());
                return ShipmentPushResult.failure(errorCode, errorMessage, e.getResponseBodyAsString());
            } catch (Exception parseEx) {
                return ShipmentPushResult.failure(
                        "HTTP_" + e.getStatusCode().value(),
                        e.getMessage(),
                        e.getResponseBodyAsString()
                );
            }
            
        } catch (Exception e) {
            log.error("[SmartStore] 송장 반영 오류", e);
            return ShipmentPushResult.failure("API_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean testConnection(String credentials) {
        try {
            SmartStoreCredentials creds = SmartStoreCredentials.parse(credentials);
            String accessToken = tokenService.getAccessToken(creds);
            return accessToken != null && !accessToken.isBlank();
        } catch (Exception e) {
            log.error("[SmartStore] 연결 테스트 실패", e);
            return false;
        }
    }
}
