package com.sellsync.api.infra.marketplace.coupang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 쿠팡 카테고리 API 클라이언트
 *
 * 엔드포인트: GET /v2/providers/seller_api/apis/api/v1/marketplace/meta/display-categories
 * 용도: 전체 display 카테고리 트리 조회 (코드 + 한글명 + 부모 관계)
 */
@Component
@Slf4j
public class CoupangCategoryClient {

    private static final String BASE_URL = "https://api-gateway.coupang.com";
    private static final String CATEGORIES_PATH =
            "/v2/providers/seller_api/apis/api/v1/marketplace/meta/display-categories";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CoupangHmacGenerator hmacGenerator;
    private final CircuitBreaker coupangCircuitBreaker;
    private final Retry coupangRetry;

    public CoupangCategoryClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            CoupangHmacGenerator hmacGenerator,
            CircuitBreaker coupangCircuitBreaker,
            Retry coupangRetry) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.hmacGenerator = hmacGenerator;
        this.coupangCircuitBreaker = coupangCircuitBreaker;
        this.coupangRetry = coupangRetry;
    }

    /**
     * 쿠팡 전체 display 카테고리 조회
     *
     * @param credentials 쿠팡 API 인증 JSON
     * @return 플래튼된 카테고리 목록 (트리 → 리스트)
     */
    public List<DisplayCategoryDto> fetchAllDisplayCategories(String credentials) {
        try {
            CoupangCredentials creds = CoupangCredentials.parse(credentials);
            String authorization = hmacGenerator.generateAuthorization(creds, "GET", CATEGORIES_PATH, "");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorization);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = BASE_URL + CATEGORIES_PATH;
            log.info("[CoupangCategory] 카테고리 전체 조회 API 호출: url={}", url);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            Supplier<ResponseEntity<String>> decoratedSupplier =
                    Retry.decorateSupplier(coupangRetry,
                            CircuitBreaker.decorateSupplier(coupangCircuitBreaker,
                                    () -> restTemplate.exchange(url, HttpMethod.GET, request, String.class)));

            ResponseEntity<String> response = decoratedSupplier.get();

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String body = response.getBody();
                // 응답 구조 확인용 로깅 (최대 2000자)
                log.info("[CoupangCategory] API 응답 원본 (앞 2000자): {}",
                        body.length() > 2000 ? body.substring(0, 2000) + "..." : body);
                List<DisplayCategoryDto> categories = parseCategoriesResponse(body);
                log.info("[CoupangCategory] 카테고리 조회 완료: {}개", categories.size());
                return categories;
            }

            log.warn("[CoupangCategory] 카테고리 조회 실패: status={}", response.getStatusCode());
            return Collections.emptyList();

        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.error("[CoupangCategory] Circuit breaker OPEN");
            return Collections.emptyList();
        } catch (HttpClientErrorException e) {
            log.error("[CoupangCategory] API 오류: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[CoupangCategory] 카테고리 조회 실패: error={}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 실제 응답 구조:
     * {
     *   "code": "SUCCESS",
     *   "data": {
     *     "displayItemCategoryCode": 0,
     *     "name": "ROOT",
     *     "child": [
     *       { "displayItemCategoryCode": 69182, "name": "패션의류잡화", "child": [...] }
     *     ]
     *   }
     * }
     */
    private List<DisplayCategoryDto> parseCategoriesResponse(String body) {
        try {
            List<DisplayCategoryDto> result = new ArrayList<>();
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");

            if (data.isMissingNode()) {
                log.warn("[CoupangCategory] 응답에 data 없음");
                return result;
            }

            // data는 ROOT 단일 객체 — ROOT의 child부터 depth 0으로 시작
            JsonNode children = data.path("child");
            if (children.isArray()) {
                for (JsonNode node : children) {
                    flattenCategory(node, null, 0, result);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("[CoupangCategory] 응답 파싱 실패: error={}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private void flattenCategory(JsonNode node, String parentCode, int depth,
                                 List<DisplayCategoryDto> result) {
        // 실제 필드명: displayItemCategoryCode, name
        String code = null;
        JsonNode codeNode = node.path("displayItemCategoryCode");
        if (!codeNode.isMissingNode()) {
            code = String.valueOf(codeNode.asLong());
        }
        String name = node.path("name").asText(null);

        if (code == null || "0".equals(code) || name == null) return;

        result.add(DisplayCategoryDto.builder()
                .displayCategoryCode(code)
                .displayCategoryName(name)
                .parentCategoryCode(parentCode)
                .depth(depth)
                .build());

        // 하위 카테고리: "child" 배열
        JsonNode children = node.path("child");
        if (children.isArray()) {
            for (JsonNode child : children) {
                flattenCategory(child, code, depth + 1, result);
            }
        }
    }

    @Data
    @Builder
    public static class DisplayCategoryDto {
        private String displayCategoryCode;
        private String displayCategoryName;
        private String parentCategoryCode;
        private int depth;
    }
}
