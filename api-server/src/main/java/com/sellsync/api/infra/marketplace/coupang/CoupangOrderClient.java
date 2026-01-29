package com.sellsync.api.infra.marketplace.coupang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.order.client.MarketplaceOrderClient;
import com.sellsync.api.domain.order.dto.MarketplaceOrderDto;
import com.sellsync.api.domain.order.dto.MarketplaceOrderItemDto;
import com.sellsync.api.domain.order.enums.Marketplace;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 쿠팡 주문 수집 클라이언트 (WING API)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CoupangOrderClient implements MarketplaceOrderClient {

    private static final String BASE_URL = "https://api-gateway.coupang.com";
    private static final String ORDERS_PATH = "/v2/providers/openapi/apis/api/v4/vendors/{vendorId}/ordersheets";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CoupangHmacGenerator hmacGenerator;
    private final CircuitBreaker coupangCircuitBreaker;
    private final Retry coupangRetry;

    @Override
    public Marketplace getMarketplace() {
        return Marketplace.COUPANG;
    }

    @Override
    public List<MarketplaceOrderDto> fetchOrders(String credentials, LocalDateTime from, LocalDateTime to) {
        CoupangCredentials creds = CoupangCredentials.parse(credentials);

        List<MarketplaceOrderDto> allOrders = new ArrayList<>();

        // 상태별 조회
        String[] statuses = {"ACCEPT", "INSTRUCT", "DEPARTURE", "DELIVERING", "FINAL_DELIVERY"};
        
        for (String status : statuses) {
            try {
                List<MarketplaceOrderDto> orders = fetchOrdersByStatus(creds, from, to, status);
                allOrders.addAll(orders);
                log.info("[Coupang] Fetched {} orders with status {}", orders.size(), status);
            } catch (Exception e) {
                log.warn("[Coupang] Failed to fetch orders with status {}: {}", status, e.getMessage());
            }
        }

        // 중복 제거
        return allOrders.stream()
                .collect(java.util.stream.Collectors.toMap(
                        MarketplaceOrderDto::getMarketplaceOrderId,
                        o -> o,
                        (o1, o2) -> o1
                ))
                .values()
                .stream()
                .toList();
    }

    /**
     * 특정 상태의 주문 조회 (페이징 처리)
     */
    private List<MarketplaceOrderDto> fetchOrdersByStatus(
            CoupangCredentials creds, LocalDateTime from, LocalDateTime to, String status) {
        // from = to.minusMonths(1);
        List<MarketplaceOrderDto> allOrders = new ArrayList<>();
        String nextToken = null;
        int pageCount = 0;
        
        do {
            pageCount++;
            log.debug("[Coupang] 상태 {} 페이지 {} 조회 중...", status, pageCount);
            
            try {
                // API 호출
                String responseBody = fetchOrdersPage(creds, from, to, status, nextToken);
                
                if (responseBody == null) {
                    break;
                }
                
                // 응답 파싱
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode dataNode = root.path("data");
                
                // 주문 데이터 파싱
                if (dataNode.isArray()) {
                    for (JsonNode orderNode : dataNode) {
                        allOrders.addAll(convertOrderItems(orderNode));
                    }
                }
                
                log.debug("[Coupang] 페이지 {} 조회 완료: {} 건 (누적: {} 건)", 
                    pageCount, dataNode.size(), allOrders.size());
                
                // nextToken 확인
                nextToken = root.path("nextToken").asText(null);
                
                // nextToken이 비어있으면 더 이상 페이지가 없음
                if (nextToken == null || nextToken.isEmpty()) {
                    break;
                }
                
            } catch (Exception e) {
                log.error("[Coupang] 페이지 {} 조회 실패: {}", pageCount, e.getMessage(), e);
                break;
            }
            
        } while (nextToken != null && !nextToken.isEmpty());
        
        log.info("[Coupang] 상태 {} 전체 {} 페이지, {} 건 조회 완료", status, pageCount, allOrders.size());
        return allOrders;
    }
    
    /**
     * 주문 API 단일 페이지 조회
     */
    private String fetchOrdersPage(
            CoupangCredentials creds, LocalDateTime from, LocalDateTime to, 
            String status, String nextToken) {
        
        String path = ORDERS_PATH.replace("{vendorId}", creds.getVendorId());
        
        // 쿼리 파라미터 구성
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("createdAtFrom=").append(from.format(DATE_FORMAT));
        queryBuilder.append("&createdAtTo=").append(to.format(DATE_FORMAT));
        queryBuilder.append("&status=").append(status);
        queryBuilder.append("&maxPerPage=50"); // 페이지당 최대 건수
        
        // nextToken이 있으면 추가
        if (nextToken != null && !nextToken.isEmpty()) {
            queryBuilder.append("&nextToken=").append(nextToken);
        }
        
        String query = queryBuilder.toString();
        String authorization = hmacGenerator.generateAuthorization(creds, "GET", path, query);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authorization);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", java.util.UUID.randomUUID().toString());

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + path)
                .query(query)
                .build()
                .toUriString();

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            // Circuit Breaker + Retry 적용: 장애 시 빠른 실패 + 일시적 오류 재시도
            Supplier<ResponseEntity<String>> decoratedSupplier =
                    Retry.decorateSupplier(coupangRetry,
                            CircuitBreaker.decorateSupplier(coupangCircuitBreaker,
                                    () -> restTemplate.exchange(
                                            url,
                                            HttpMethod.GET,
                                            request,
                                            String.class)));

            ResponseEntity<String> response = decoratedSupplier.get();

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            return null;

        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.error("[Coupang] Circuit breaker OPEN - API calls blocked: {}", e.getMessage());
            throw new RuntimeException("Coupang API circuit breaker open", e);
        } catch (HttpClientErrorException e) {
            log.error("[Coupang] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Coupang API error: " + e.getMessage(), e);
        }
    }

    /**
     * API 응답 파싱
     * orderItems 배열의 각 아이템을 별도 주문으로 분리
     */
    private List<MarketplaceOrderDto> parseOrdersResponse(String responseBody) {
        List<MarketplaceOrderDto> orders = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataNode = root.path("data");
            
            if (dataNode.isArray()) {
                for (JsonNode orderNode : dataNode) {
                    // orderItems 배열의 각 아이템을 별도 주문으로 변환
                    orders.addAll(convertOrderItems(orderNode));
                }
            }
        } catch (Exception e) {
            log.error("[Coupang] Failed to parse response", e);
        }
        
        return orders;
    }

    /**
     * 쿠팡 주문 JSON → 통합 DTO 변환
     * orderItems 배열의 각 아이템을 별도 주문으로 분리
     * 
     * 쿠팡 API 구조:
     * - shipmentBoxId: 배송 단위 ID (발주 ID)
     * - orderId: 원래 주문 번호 (하나의 orderId가 여러 shipmentBoxId로 분할될 수 있음)
     * - orderItems: 주문 상품 배열 (각 아이템을 별도 주문으로 처리)
     */
    private List<MarketplaceOrderDto> convertOrderItems(JsonNode orderNode) {
        List<MarketplaceOrderDto> orders = new ArrayList<>();
        
        JsonNode itemsNode = orderNode.path("orderItems");
        if (!itemsNode.isArray() || itemsNode.size() == 0) {
            return orders;
        }

        // orderItems 배열의 각 아이템마다 별도 주문 생성
        // ✅ 정산 매칭을 위해 index 추가: orderId_vendorItemId_index
        int index = 0;
        for (JsonNode itemNode : itemsNode) {
            long vendorItemId = itemNode.path("vendorItemId").asLong();
            long productId = itemNode.path("productId").asLong();
            long sellerProductId = itemNode.path("sellerProductId").asLong(0);
            // marketplaceItemId는 아이템 식별용으로만 사용
            String marketplaceItemId = String.valueOf(vendorItemId);

            // 가격 정보 추출
            long orderPrice = itemNode.path("orderPrice").asLong(0);
            int quantity = itemNode.path("shippingCount").asInt(1);

            // 아이템 DTO 생성
            MarketplaceOrderItemDto item = MarketplaceOrderItemDto.builder()
                    .marketplaceItemId(marketplaceItemId)
                    .marketplaceProductId(String.valueOf(productId))
                    .marketplaceSku(String.valueOf(vendorItemId))
                    .productName(itemNode.path("vendorItemName").asText(""))
                    .exposedProductName(itemNode.path("sellerProductName").asText(null))
                    .optionName(itemNode.path("sellerProductItemName").asText(null))
                    .quantity(quantity)
                    .unitPrice(quantity > 0 ? orderPrice / quantity : orderPrice)
                    .lineAmount(orderPrice)
                    .rawPayload(itemNode.toString())
                    .sellerProductId(sellerProductId > 0 ? String.valueOf(sellerProductId) : null)
                    .build();
            
            // 주문 DTO 생성 (각 아이템당 1개)
            MarketplaceOrderDto order = MarketplaceOrderDto.builder()
                    .rawPayload(orderNode.toString())
                    .bundleOrderId(String.valueOf(orderNode.path("shipmentBoxId").asLong()))
                    // ✅ marketplaceOrderId: orderId_vendorItemId_index (정산과 매칭)
                    .marketplaceOrderId(String.valueOf(orderNode.path("orderId").asLong()) + "_" + vendorItemId + "_" + index)
                    .orderStatus(mapStatus(orderNode.path("status").asText()))
                    .orderedAt(parseDateTime(orderNode.path("orderedAt").asText()))
                    .paidAt(parseDateTime(orderNode.path("paidAt").asText()))
                    // 주문자
                    .buyerName(orderNode.path("orderer").path("name").asText(""))
                    .buyerPhone(orderNode.path("orderer").path("email").asText(null))
                    // 수취인
                    .receiverName(orderNode.path("receiver").path("name").asText(""))
                    .receiverPhone1(orderNode.path("receiver").path("receiverPhoneNumber1").asText(null))
                    .receiverPhone2(orderNode.path("receiver").path("safeNumber").asText(null))
                    .receiverZipCode(orderNode.path("receiver").path("postCode").asText(null))
                    .receiverAddress(buildAddress(orderNode.path("receiver")))
                    .safeNumber(orderNode.path("receiver").path("safeNumber").asText(null))
                    // 금액 (개별 아이템 금액)
                    .totalPaidAmount(orderPrice)
                    .totalProductAmount(orderPrice)
                    // 배송비 (쿠팡은 price 객체 형태로 제공)
                    .shippingFee(orderNode.path("shippingPrice").asLong(0))
                    .additionalShippingFee(extractPrice(orderNode.path("remotePrice")))
                    // 기타
                    .deliveryRequest(orderNode.path("parcelPrintMessage").asText(null))
                    .personalCustomsCode(orderNode.path("overseaShippingInfoDto").path("personalCustomsClearanceCode").asText(null))
                    // 상품 (1개 아이템만 포함)
                    .items(List.of(item))
                    .build();
            
            orders.add(order);
            index++;
        }
        
        return orders;
    }

    /**
     * 주문 상품 변환
     * 
     * 쿠팡 API 주의사항:
     * - vendorItemId: 판매자 상품 ID (같은 상품이면 동일)
     * - 주문 내 복수 상품 구분을 위해 vendorItemId + 배열 인덱스 조합 사용
     * - 실제 API에 orderItemId가 있다면 그것을 사용하는 것이 더 안전함
     */
    private List<MarketplaceOrderItemDto> convertItems(JsonNode itemsNode) {
        List<MarketplaceOrderItemDto> items = new ArrayList<>();
        
        if (itemsNode.isArray()) {
            int index = 0;
            for (JsonNode item : itemsNode) {
                // vendorItemId를 기본으로 사용하되, 동일 주문 내 동일 상품 중복 시 인덱스 추가
                long vendorItemId = item.path("vendorItemId").asLong();
                long productId = item.path("productId").asLong();
                long sellerProductId = item.path("sellerProductId").asLong(0);
                String marketplaceItemId = String.valueOf(vendorItemId);
                
                // 만약 API 응답에 orderItemId나 shipmentBoxItemId 같은 필드가 있다면 그것을 사용
                if (item.has("orderItemId")) {
                    marketplaceItemId = String.valueOf(item.path("orderItemId").asLong());
                } else if (item.has("shipmentBoxItemId")) {
                    marketplaceItemId = String.valueOf(item.path("shipmentBoxItemId").asLong());
                } else {
                    // 폴백: vendorItemId + 인덱스 조합
                    marketplaceItemId = vendorItemId + "_" + index;
                }
                
                // 가격 정보 추출 (쿠팡은 price 객체 형태로 제공)
                long salesPrice = extractPrice(item.path("salesPrice"));  // 판매가
                long orderPrice = extractPrice(item.path("orderPrice"));  // 주문가 (할인 후)
                int quantity = item.path("shippingCount").asInt(1);
                
                items.add(MarketplaceOrderItemDto.builder()
                        .marketplaceItemId(marketplaceItemId)  // ✅ 쿠팡 상품라인 고유ID
                        .marketplaceProductId(String.valueOf(productId))
                        .marketplaceSku(String.valueOf(vendorItemId))
                        .productName(item.path("vendorItemName").asText(""))
                        .exposedProductName(item.path("sellerProductName").asText(null))
                        .optionName(item.path("sellerProductItemName").asText(null))
                        .quantity(quantity)
                        .unitPrice(quantity > 0 ? orderPrice / quantity : orderPrice)  // ✅ 단가 = 주문가 / 수량
                        .lineAmount(orderPrice)  // ✅ 라인 금액 = 주문가
                        .rawPayload(item.toString())
                        .sellerProductId(sellerProductId > 0 ? String.valueOf(sellerProductId) : null)
                        .build());

                index++;
            }
        }
        
        return items;
    }

    /**
     * 주문 상태 매핑
     */
    private String mapStatus(String status) {
        if (status == null) return "NEW";
        return switch (status) {
            case "ACCEPT" -> "NEW";
            case "INSTRUCT" -> "PREPARING";
            case "DEPARTURE", "DELIVERING" -> "SHIPPING";
            case "FINAL_DELIVERY" -> "DELIVERED";
            case "CANCEL" -> "CANCELED";
            default -> "CONFIRMED";
        };
    }

    /**
     * 날짜 파싱
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return LocalDateTime.now();
        try {
            // 쿠팡 날짜 포맷: yyyy-MM-dd'T'HH:mm:ss
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * 주소 빌드
     */
    private String buildAddress(JsonNode receiver) {
        String addr = receiver.path("addr1").asText("");
        String detail = receiver.path("addr2").asText("");
        return (addr + " " + detail).trim();
    }

    /**
     * 쿠팡 Price 객체에서 금액 추출
     * 
     * 쿠팡 API는 price를 다음 형태로 제공:
     * {
     *   "currencyCode": "KRW",
     *   "units": 23000,
     *   "nanos": 0
     * }
     */
    private long extractPrice(JsonNode priceNode) {
        if (priceNode == null || priceNode.isMissingNode()) {
            return 0L;
        }
        return priceNode.path("units").asLong(0);
    }
}
