package com.sellsync.api.infra.marketplace.coupang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 쿠팡 상품 조회 API 클라이언트 (WING API)
 *
 * 엔드포인트: GET /v2/providers/seller_api/apis/api/v1/marketplace/seller-products/{sellerProductId}
 * 용도: 상품의 수수료율(saleAgentCommission), 카테고리코드(displayCategoryCode) 조회
 */
@Component
@Slf4j
public class CoupangProductClient {

    private static final String BASE_URL = "https://api-gateway.coupang.com";
    private static final String PRODUCT_PATH = "/v2/providers/seller_api/apis/api/v1/marketplace/seller-products/{sellerProductId}";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CoupangHmacGenerator hmacGenerator;
    private final CircuitBreaker coupangCircuitBreaker;
    private final Retry coupangRetry;
    private final CoupangCommissionRateService commissionRateService;

    public CoupangProductClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            CoupangHmacGenerator hmacGenerator,
            CircuitBreaker coupangCircuitBreaker,
            Retry coupangRetry,
            CoupangCommissionRateService commissionRateService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.hmacGenerator = hmacGenerator;
        this.coupangCircuitBreaker = coupangCircuitBreaker;
        this.coupangRetry = coupangRetry;
        this.commissionRateService = commissionRateService;
    }

    /**
     * 쿠팡 상품 정보 조회
     *
     * @param credentials 쿠팡 API 인증 JSON
     * @param sellerProductId 쿠팡 sellerProductId
     * @return 상품 정보 (수수료율, 카테고리코드 포함)
     */
    public Optional<CoupangProductInfo> fetchProductInfo(String credentials, String sellerProductId) {
        try {
            CoupangCredentials creds = CoupangCredentials.parse(credentials);
            String path = PRODUCT_PATH.replace("{sellerProductId}", sellerProductId);
            String authorization = hmacGenerator.generateAuthorization(creds, "GET", path, "");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorization);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = BASE_URL + path;
            log.info("[CoupangProduct] API 호출: url={}", url);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            Supplier<ResponseEntity<String>> decoratedSupplier =
                    Retry.decorateSupplier(coupangRetry,
                            CircuitBreaker.decorateSupplier(coupangCircuitBreaker,
                                    () -> restTemplate.exchange(url, HttpMethod.GET, request, String.class)));

            ResponseEntity<String> response = decoratedSupplier.get();

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("[CoupangProduct] API 응답 성공 - sellerProductId={}, bodyLength={}",
                        sellerProductId, response.getBody().length());
                return parseProductInfo(sellerProductId, response.getBody());
            }

            log.warn("[CoupangProduct] 상품 조회 실패 - sellerProductId={}, status={}",
                    sellerProductId, response.getStatusCode());
            return Optional.empty();

        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.error("[CoupangProduct] Circuit breaker OPEN - sellerProductId={}", sellerProductId);
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.error("[CoupangProduct] API 오류 - sellerProductId={}, status={}, body={}",
                    sellerProductId, e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("[CoupangProduct] 상품 조회 실패 - sellerProductId={}, error={}",
                    sellerProductId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 상품 API 응답 파싱
     *
     * 응답 구조:
     * {
     *   "code": "SUCCESS",
     *   "data": {
     *     "displayCategoryCode": 56137,
     *     "items": [
     *       {
     *         "vendorItemId": 12345,
     *         "saleAgentCommission": 9.0
     *       }
     *     ]
     *   }
     * }
     */
    private Optional<CoupangProductInfo> parseProductInfo(String sellerProductId, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");

            if (data.isMissingNode()) {
                log.warn("[CoupangProduct] 응답에 data 없음 - sellerProductId={}", sellerProductId);
                return Optional.empty();
            }

            // data 레벨 필드 로깅 (수수료 관련 필드 탐색용)
            log.info("[CoupangProduct] data 레벨 필드 목록: {}", iteratorToString(data.fieldNames()));

            String displayCategoryCode = data.path("displayCategoryCode").asText(null);

            // items 배열에서 수수료율 추출
            BigDecimal commissionRate = null;
            Map<String, BigDecimal> itemCommissions = new HashMap<>();

            JsonNode itemsNode = data.path("items");
            if (itemsNode.isArray() && itemsNode.size() > 0) {
                JsonNode firstItem = itemsNode.get(0);
                log.info("[CoupangProduct] items[0] 필드 목록: {}", iteratorToString(firstItem.fieldNames()));

                for (JsonNode item : itemsNode) {
                    double commission = item.path("saleAgentCommission").asDouble(0);
                    if (commission > 0) {
                        BigDecimal rate = BigDecimal.valueOf(commission);
                        String vendorItemId = String.valueOf(item.path("vendorItemId").asLong());
                        itemCommissions.put(vendorItemId, rate);

                        if (commissionRate == null) {
                            commissionRate = rate;
                        }
                    }
                }
            }

            // saleAgentCommission이 0인 경우 displayCategoryCode 기반 DB 조회 폴백
            if (commissionRate == null) {
                BigDecimal dbRate = commissionRateService.getCommissionRate(displayCategoryCode);
                if (dbRate != null) {
                    commissionRate = dbRate;
                    log.info("[CoupangProduct] saleAgentCommission 없음 → DB 매핑 수수료율 적용: sellerProductId={}, displayCategoryCode={}, rate={}",
                            sellerProductId, displayCategoryCode, dbRate);
                } else {
                    log.warn("[CoupangProduct] saleAgentCommission 없음 + 미매핑 → 수수료율 미설정: sellerProductId={}, displayCategoryCode={}",
                            sellerProductId, displayCategoryCode);
                }
            }

            log.info("[CoupangProduct] 파싱 결과 - sellerProductId={}, displayCategoryCode={}, commissionRate={}",
                    sellerProductId, displayCategoryCode, commissionRate);

            CoupangProductInfo info = CoupangProductInfo.builder()
                    .sellerProductId(sellerProductId)
                    .displayCategoryCode(displayCategoryCode)
                    .saleAgentCommission(commissionRate)
                    .itemCommissions(itemCommissions)
                    .build();

            log.debug("[CoupangProduct] 상품 정보 조회 성공 - sellerProductId={}, category={}, commission={}",
                    sellerProductId, displayCategoryCode, commissionRate);

            return Optional.of(info);

        } catch (Exception e) {
            log.warn("[CoupangProduct] 응답 파싱 실패 - sellerProductId={}, error={}",
                    sellerProductId, e.getMessage());
            return Optional.empty();
        }
    }

    private String iteratorToString(java.util.Iterator<String> iterator) {
        StringBuilder sb = new StringBuilder();
        while (iterator.hasNext()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(iterator.next());
        }
        return sb.toString();
    }
}
