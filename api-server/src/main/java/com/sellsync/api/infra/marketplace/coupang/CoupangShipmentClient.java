package com.sellsync.api.infra.marketplace.coupang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.client.MarketShipmentClient;
import com.sellsync.api.domain.shipping.dto.ShipmentPushRequest;
import com.sellsync.api.domain.shipping.dto.ShipmentPushResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 쿠팡 송장 반영 클라이언트 (WING API)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CoupangShipmentClient implements MarketShipmentClient {

    private static final String BASE_URL = "https://api-gateway.coupang.com";
    private static final String INVOICE_PATH = "/v2/providers/openapi/apis/api/v4/vendors/{vendorId}/ordersheets/{shipmentBoxId}/invoices";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CoupangHmacGenerator hmacGenerator;

    @Override
    public Marketplace getMarketplace() {
        return Marketplace.COUPANG;
    }

    @Override
    public ShipmentPushResult pushShipment(String credentials, ShipmentPushRequest request) {
        log.info("[Coupang] 송장 반영 시작: shipmentBoxId={}, tracking={}", 
                request.getShipmentBoxId(), 
                request.getTrackingNo());

        try {
            // 인증 정보 파싱
            CoupangCredentials creds = CoupangCredentials.parse(credentials);

            // URL 구성
            String path = INVOICE_PATH
                    .replace("{vendorId}", creds.getVendorId())
                    .replace("{shipmentBoxId}", request.getShipmentBoxId());
            String url = BASE_URL + path;

            // HMAC 서명 생성
            String authorization = hmacGenerator.generateAuthorization(
                    creds,
                    "PUT",
                    path,
                    ""
            );

            // 헤더 구성
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);

            // 택배사 코드 매핑
            String coupangDeliveryCode = mapToCoupangDeliveryCode(request.getCarrierCode());

            // 요청 바디 구성
            Map<String, Object> body = new HashMap<>();
            body.put("vendorId", creds.getVendorId());
            body.put("shipmentBoxId", Long.parseLong(request.getShipmentBoxId()));
            body.put("deliveryCompanyCode", coupangDeliveryCode);
            body.put("invoiceNumber", request.getTrackingNo());

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

            // API 호출
            ResponseEntity<String> response = restTemplate.exchange(
                    url, 
                    HttpMethod.PUT, 
                    httpRequest, 
                    String.class
            );

            // 응답 처리
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String code = root.path("code").asText();
                
                if ("200".equals(code) || "SUCCESS".equalsIgnoreCase(code)) {
                    log.info("[Coupang] 송장 반영 성공: tracking={}", request.getTrackingNo());
                    return ShipmentPushResult.success(response.getBody());
                } else {
                    String errorMsg = root.path("message").asText("Unknown error");
                    log.warn("[Coupang] 송장 반영 실패: {} - {}", code, errorMsg);
                    return ShipmentPushResult.failure(code, errorMsg, response.getBody());
                }
            } else {
                String errorCode = "HTTP_" + response.getStatusCode().value();
                String errorMessage = "송장 반영 실패: " + response.getStatusCode();
                log.warn("[Coupang] 송장 반영 실패: {} - {}", errorCode, errorMessage);
                return ShipmentPushResult.failure(errorCode, errorMessage, response.getBody());
            }

        } catch (HttpClientErrorException e) {
            log.error("[Coupang] API 오류: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            
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
            log.error("[Coupang] 송장 반영 오류", e);
            return ShipmentPushResult.failure("API_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean testConnection(String credentials) {
        try {
            CoupangCredentials creds = CoupangCredentials.parse(credentials);
            return creds.getVendorId() != null && 
                   creds.getAccessKey() != null && 
                   creds.getSecretKey() != null;
        } catch (Exception e) {
            log.error("[Coupang] 연결 테스트 실패", e);
            return false;
        }
    }

    /**
     * 내부 택배사 코드를 쿠팡 택배사 코드로 매핑
     */
    private String mapToCoupangDeliveryCode(String carrierCode) {
        return switch (carrierCode) {
            case "CJGLS" -> "CJGLS";
            case "HANJIN" -> "HANJIN";
            case "LOTTE" -> "LOTTE";
            case "LOGEN" -> "LOGEN";
            case "EPOST" -> "EPOST";
            default -> "CJGLS"; // 기본값
        };
    }
}
