package com.sellsync.api.infra.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private final TossPaymentsProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 빌링키 발급
     */
    @CircuitBreaker(name = "tossPayments", fallbackMethod = "issueBillingKeyFallback")
    @Retry(name = "tossPayments")
    public Map<String, Object> issueBillingKey(String authKey, String customerKey) {
        String url = properties.getBillingUrl() + "/authorizations/issue";

        Map<String, String> body = new HashMap<>();
        body.put("authKey", authKey);
        body.put("customerKey", customerKey);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, createHeaders());

        log.info("[토스 빌링키 발급 요청] customerKey={}", customerKey);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            Map<String, Object> result = new HashMap<>();
            result.put("billingKey", node.path("billingKey").asText());
            result.put("customerKey", node.path("customerKey").asText());

            JsonNode card = node.path("card");
            if (!card.isMissingNode()) {
                result.put("cardCompany", card.path("issuerCode").asText());
                result.put("cardNumber", card.path("number").asText());
                result.put("cardType", card.path("cardType").asText());
            }

            log.info("[토스 빌링키 발급 성공] customerKey={}", customerKey);
            return result;
        } catch (Exception e) {
            log.error("[토스 빌링키 응답 파싱 실패] customerKey={}, error={}", customerKey, e.getMessage());
            throw new RuntimeException("빌링키 발급 응답 파싱 실패", e);
        }
    }

    /**
     * 빌링키로 자동결제 실행
     */
    @CircuitBreaker(name = "tossPayments", fallbackMethod = "requestBillingPaymentFallback")
    @Retry(name = "tossPayments")
    public Map<String, Object> requestBillingPayment(String billingKey, int amount, String orderId, String orderName) {
        String url = properties.getBillingUrl() + "/" + billingKey;

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("orderId", orderId);
        body.put("orderName", orderName);
        body.put("customerKey", orderId.split("_")[0]); // tenantId prefix

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, createHeaders());

        log.info("[토스 자동결제 요청] billingKey={}, amount={}, orderId={}", billingKey, amount, orderId);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            Map<String, Object> result = new HashMap<>();
            result.put("paymentKey", node.path("paymentKey").asText());
            result.put("status", node.path("status").asText());
            result.put("approvedAt", node.path("approvedAt").asText());
            result.put("totalAmount", node.path("totalAmount").asInt());

            log.info("[토스 자동결제 성공] orderId={}, paymentKey={}", orderId, result.get("paymentKey"));
            return result;
        } catch (Exception e) {
            log.error("[토스 자동결제 응답 파싱 실패] orderId={}, error={}", orderId, e.getMessage());
            throw new RuntimeException("자동결제 응답 파싱 실패", e);
        }
    }

    /**
     * 결제 취소
     */
    @CircuitBreaker(name = "tossPayments", fallbackMethod = "cancelPaymentFallback")
    public Map<String, Object> cancelPayment(String paymentKey, String cancelReason) {
        String url = properties.getPaymentUrl() + "/" + paymentKey + "/cancel";

        Map<String, String> body = new HashMap<>();
        body.put("cancelReason", cancelReason);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, createHeaders());

        log.info("[토스 결제 취소 요청] paymentKey={}, reason={}", paymentKey, cancelReason);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            Map<String, Object> result = new HashMap<>();
            result.put("paymentKey", node.path("paymentKey").asText());
            result.put("status", node.path("status").asText());

            log.info("[토스 결제 취소 성공] paymentKey={}", paymentKey);
            return result;
        } catch (Exception e) {
            log.error("[토스 결제 취소 응답 파싱 실패] paymentKey={}, error={}", paymentKey, e.getMessage());
            throw new RuntimeException("결제 취소 응답 파싱 실패", e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String credentials = properties.getSecretKey() + ":";
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encoded);

        return headers;
    }

    // Fallback methods
    @SuppressWarnings("unused")
    private Map<String, Object> issueBillingKeyFallback(String authKey, String customerKey, Throwable t) {
        log.error("[토스 빌링키 발급 서킷브레이커] customerKey={}, error={}", customerKey, t.getMessage());
        throw new RuntimeException("결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", t);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> requestBillingPaymentFallback(String billingKey, int amount, String orderId, String orderName, Throwable t) {
        log.error("[토스 자동결제 서킷브레이커] orderId={}, error={}", orderId, t.getMessage());
        throw new RuntimeException("결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", t);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> cancelPaymentFallback(String paymentKey, String cancelReason, Throwable t) {
        log.error("[토스 결제 취소 서킷브레이커] paymentKey={}, error={}", paymentKey, t.getMessage());
        throw new RuntimeException("결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", t);
    }
}
