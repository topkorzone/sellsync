package com.sellsync.api.infra.marketplace.smartstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.order.client.MarketplaceOrderClient;
import com.sellsync.api.domain.order.dto.MarketplaceOrderDto;
import com.sellsync.api.domain.order.dto.MarketplaceOrderItemDto;
import com.sellsync.api.domain.order.enums.Marketplace;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

/**
 * 네이버 스마트스토어 주문 수집 클라이언트 (커머스 API)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SmartStoreOrderClient implements MarketplaceOrderClient {

    private static final String BASE_URL = "https://api.commerce.naver.com";
    private static final String ORDERS_ENDPOINT = "/external/v1/pay-order/seller/product-orders";
    // 네이버 API는 ISO-8601 형식 + 밀리초 3자리 + 타임존 필수 (예: 2024-06-07T19:00:00.000+09:00)
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SmartStoreTokenService tokenService;

    @Override
    public Marketplace getMarketplace() {
        return Marketplace.NAVER_SMARTSTORE;
    }

    @Override
    public List<MarketplaceOrderDto> fetchOrders(String credentials, LocalDateTime from, LocalDateTime to) {
        SmartStoreCredentials creds = SmartStoreCredentials.parse(credentials);
        String accessToken = tokenService.getAccessToken(creds);

        // 스마트스토어 API 제약: 24시간 이내만 조회 가능
        // 전체 기간을 24시간씩 나눠서 여러 번 호출
        List<MarketplaceOrderDto> allOrders = new ArrayList<>();
        
        LocalDateTime currentFrom = from;
        int batchCount = 0;
        
        while (currentFrom.isBefore(to)) {
            // 24시간 단위로 분할 (마지막 구간은 to까지)
            LocalDateTime currentTo = currentFrom.plusHours(24);
            if (currentTo.isAfter(to)) {
                currentTo = to;
            }
            
            batchCount++;
            log.info("[SmartStore] Batch {}: Fetching orders from {} to {}", 
                    batchCount, currentFrom, currentTo);
            
            try {
                List<MarketplaceOrderDto> batchOrders = fetchOrdersByDateRange(accessToken, currentFrom, currentTo);
                allOrders.addAll(batchOrders);
                log.info("[SmartStore] Batch {}: Fetched {} orders", batchCount, batchOrders.size());
                
                // API Rate Limit 방지: 요청 간 딜레이 (1초)
                if (currentTo.isBefore(to)) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[SmartStore] Batch {} interrupted", batchCount);
                break;
            } catch (Exception e) {
                log.error("[SmartStore] Batch {} failed: {}", batchCount, e.getMessage(), e);
                throw e;
            }
            
            currentFrom = currentTo;
        }
        
        log.info("[SmartStore] Total fetched {} orders from {} batches", allOrders.size(), batchCount);
        return allOrders;
    }

    /**
     * 날짜 범위로 주문 조회 (네이버 커머스 API 표준 엔드포인트)
     */
    private List<MarketplaceOrderDto> fetchOrdersByDateRange(
            String accessToken, LocalDateTime from, LocalDateTime to) {
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 네이버 커머스 API 요구사항: + 기호만 URL 인코딩 (%2B)
        // 예시: 2026-01-13T18:24:52.281%2B09:00
        ZoneId kst = ZoneId.of("Asia/Seoul");
        
        // LocalDateTime을 KST OffsetDateTime으로 변환
        OffsetDateTime fromOdt = from.atZone(kst).toOffsetDateTime();
        OffsetDateTime toOdt = to.atZone(kst).toOffsetDateTime().minusSeconds(1); // 24시간 미만으로 보정
        
        // 밀리초 3자리 포맷 (yyyy-MM-dd'T'HH:mm:ss.SSSXXX)
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        
        // 날짜 포맷팅 후 + 기호만 %2B로 수동 인코딩
        String fromStr = fromOdt.format(fmt).replace("+", "%2B");
        String toStr = toOdt.format(fmt).replace("+", "%2B");
        
        // URL 빌드 (추가 인코딩 하지 않음)
        String urlString = UriComponentsBuilder.fromHttpUrl(BASE_URL + ORDERS_ENDPOINT)
                .queryParam("from", fromStr)
                .queryParam("to", toStr)
                .queryParam("rangeType", "PAYED_DATETIME")
                .queryParam("productOrderStatuses", "PAYED", "DELIVERING", "DELIVERED", "PURCHASE_DECIDED")
                .build(false)  // false: 이미 + → %2B 처리했으므로 추가 인코딩 하지 않음
                .toUriString();

        // 이미 인코딩된 문자열을 URI 객체로 변환 (RestTemplate의 이중 인코딩 방지)
        java.net.URI uri = java.net.URI.create(urlString);

        log.debug("[SmartStore] API Request URL: {}", uri);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri,  // URI 객체 전달 → RestTemplate이 추가 인코딩하지 않음
                    HttpMethod.GET, 
                    request, 
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOrdersResponse(response.getBody());
            }
            return new ArrayList<>();
            
        } catch (HttpClientErrorException e) {
            log.error("[SmartStore] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SmartStore API error: " + e.getMessage(), e);
        }
    }

    /**
     * API 응답 파싱
     */
    private List<MarketplaceOrderDto> parseOrdersResponse(String responseBody) {
        List<MarketplaceOrderDto> orders = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // /product-orders 엔드포인트는 data.contents 구조 사용
            JsonNode dataNode = root.path("data");
            JsonNode contentsNode = dataNode.path("contents");
            
            // contents 배열이 있으면 사용, 없으면 data 배열 시도
            JsonNode targetNode = contentsNode.isArray() ? contentsNode : 
                                  dataNode.isArray() ? dataNode : null;
            
            if (targetNode != null && targetNode.isArray()) {
                for (JsonNode orderNode : targetNode) {
                    try {
                        orders.add(convertOrder(orderNode));
                    } catch (Exception e) {
                        log.warn("[SmartStore] Failed to convert order: {}", e.getMessage());
                    }
                }
            } else {
                log.warn("[SmartStore] No orders found in response. Response: {}", 
                    responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
            }
        } catch (Exception e) {
            log.error("[SmartStore] Failed to parse response", e);
        }
        
        return orders;
    }

    /**
     * 스마트스토어 주문 JSON → 통합 DTO 변환
     * 응답 구조: { "content": { "order": {...}, "productOrder": {...}, "delivery": {...} } }
     */
    private MarketplaceOrderDto convertOrder(JsonNode node) {
        // content.order, content.productOrder, content.delivery 추출
        JsonNode contentNode = node.path("content");
        JsonNode orderNode = contentNode.path("order");
        JsonNode productOrderNode = contentNode.path("productOrder");
        JsonNode deliveryNode = contentNode.path("delivery");
        
        return MarketplaceOrderDto.builder()
                .rawPayload(node.toString())
                .marketplaceOrderId(orderNode.path("orderId").asText())
                .bundleOrderId(orderNode.path("orderNo").asText(null))
                .orderStatus(mapStatus(productOrderNode.path("productOrderStatus").asText()))
                .orderedAt(parseDateTime(orderNode.path("orderDate").asText()))
                .paidAt(parseDateTime(orderNode.path("paymentDate").asText()))
                // 주문자
                .buyerName(orderNode.path("ordererName").asText(""))
                .buyerPhone(orderNode.path("ordererTel").asText(null))
                .buyerId(orderNode.path("ordererId").asText(null))
                // 수취인
                .receiverName(deliveryNode.path("receiverName").asText(""))
                .receiverPhone1(deliveryNode.path("receiverTel1").asText(null))
                .receiverPhone2(deliveryNode.path("receiverTel2").asText(null))
                .receiverZipCode(deliveryNode.path("zipCode").asText(null))
                .receiverAddress(buildDeliveryAddress(deliveryNode))
                .safeNumberType(deliveryNode.path("isRoadNameAddress").asBoolean() ? "ROAD" : "JIBUN")
                // 금액
                .totalProductAmount(productOrderNode.path("totalProductAmount").asLong(0))
                .totalDiscountAmount(orderNode.path("orderDiscountAmount").asLong(0))
                .totalPaidAmount(productOrderNode.path("totalPaymentAmount").asLong(0))
                .commissionAmount(extractCommissionAmount(productOrderNode))
                .expectedSettlementAmount(productOrderNode.path("expectedSettlementAmount").asLong(0))
                // 배송비
                .shippingFeeType(productOrderNode.path("deliveryFeeType").asText(null))
                .shippingFee(extractShippingFee(productOrderNode))
                .prepaidShippingFee(extractShippingFee(productOrderNode))
                .additionalShippingFee(productOrderNode.path("additionalDeliveryFee").asLong(0))
                // 기타
                .deliveryRequest(deliveryNode.path("deliveryMessage").asText(null))
                .paymentMethod(orderNode.path("paymentMeans").asText(null))
                .personalCustomsCode(deliveryNode.path("personalCustomsClearanceCode").asText(null))
                // 상품
                .items(convertItems(productOrderNode))
                .build();
    }

    /**
     * 주문 상품 변환
     * @param productOrderNode content.productOrder 노드
     */
    private List<MarketplaceOrderItemDto> convertItems(JsonNode productOrderNode) {
        List<MarketplaceOrderItemDto> items = new ArrayList<>();
        
        // 옵션 정보 추출 (productOrder.productOption에서 추출)
        String optionName = extractOptionName(productOrderNode);
        
        // 단일 상품 주문인 경우
        MarketplaceOrderItemDto item = MarketplaceOrderItemDto.builder()
                .marketplaceProductId(productOrderNode.path("productId").asText())
                .marketplaceSku(productOrderNode.path("productOrderId").asText())
                .productName(productOrderNode.path("productName").asText())
                .optionName(optionName)
                .quantity(productOrderNode.path("quantity").asInt(1))
                .unitPrice(productOrderNode.path("unitPrice").asLong(0))
                .originalPrice(productOrderNode.path("originalProductPrice").asLong(0))
                .discountAmount(productOrderNode.path("productDiscountAmount").asLong(0))
                .lineAmount(productOrderNode.path("totalProductAmount").asLong(0))
                .commissionAmount(extractItemCommissionAmount(productOrderNode))
                .rawPayload(productOrderNode.toString())
                .build();
        
        items.add(item);
        return items;
    }
    
    /**
     * 옵션 정보 추출
     * 스마트스토어 API의 productOption 필드에서 옵션 정보 추출
     */
    private String extractOptionName(JsonNode productOrderNode) {
        String productName = productOrderNode.path("productName").asText("");
        
        // 디버그: JSON 구조 확인
        log.debug("[SmartStore Option Debug] productOrderNode keys: {}", productOrderNode.fieldNames());
        log.debug("[SmartStore Option Debug] productOption 값: '{}'", productOrderNode.path("productOption").asText("NULL"));
        
        // productOption 필드 확인 (스마트스토어 주문 API의 옵션 정보 필드)
        String productOption = productOrderNode.path("productOption").asText(null);
        
        if (productOption != null && !productOption.isEmpty() && !productOption.equals("null")) {
            log.info("[SmartStore] ✓ Option found in productOption: {}", productOption);
            return productOption;
        }
        
        // productOption이 없는 경우 null 반환 (옵션 없는 상품)
        log.warn("[SmartStore] ✗ No option info found for product: {} (상품에 옵션이 없음)", productName);
        return null;
    }
    
    /**
     * 수수료 추출 (commission 필드 합산)
     * 스마트스토어 API의 4가지 commission 필드를 합산
     * - channelCommission: 채널 수수료
     * - saleCommission: 판매 수수료
     * - paymentCommission: 결제 수수료
     * - knowledgeShoppingSellingInterlockCommission: 지식쇼핑 연동 수수료
     */
    private Long extractCommissionAmount(JsonNode productOrderNode) {
        long channelCommission = productOrderNode.path("channelCommission").asLong(0);
        long saleCommission = productOrderNode.path("saleCommission").asLong(0);
        long paymentCommission = productOrderNode.path("paymentCommission").asLong(0);
        long knowledgeCommission = productOrderNode.path("knowledgeShoppingSellingInterlockCommission").asLong(0);
        
        long totalCommission = channelCommission + saleCommission + paymentCommission + knowledgeCommission;
        
        if (totalCommission > 0) {
            log.debug("[Commission] Total: {}, channel: {}, sale: {}, payment: {}, knowledge: {}", 
                    totalCommission, channelCommission, saleCommission, paymentCommission, knowledgeCommission);
        }
        
        return totalCommission;
    }
    
    /**
     * 상품별 수수료 추출
     * 스마트스토어 API의 4가지 commission 필드를 합산
     */
    private Long extractItemCommissionAmount(JsonNode productOrderNode) {
        long channelCommission = productOrderNode.path("channelCommission").asLong(0);
        long saleCommission = productOrderNode.path("saleCommission").asLong(0);
        long paymentCommission = productOrderNode.path("paymentCommission").asLong(0);
        long knowledgeCommission = productOrderNode.path("knowledgeShoppingSellingInterlockCommission").asLong(0);
        
        return channelCommission + saleCommission + paymentCommission + knowledgeCommission;
    }
    
    /**
     * 배송비 추출
     * 스마트스토어 배송비 우선순위:
     * 1. deliveryFeeAmount (값이 있으면 사용)
     * 2. sectionDeliveryFee (deliveryFeeAmount가 0이면 사용)
     * 3. 둘 다 0이면 0
     */
    private Long extractShippingFee(JsonNode productOrderNode) {
        long deliveryFeeAmount = productOrderNode.path("deliveryFeeAmount").asLong(0);
        if (deliveryFeeAmount > 0) {
            return deliveryFeeAmount;
        }
        
        long sectionDeliveryFee = productOrderNode.path("sectionDeliveryFee").asLong(0);
        return sectionDeliveryFee;
    }

    /**
     * 주문 상태 매핑
     */
    private String mapStatus(String status) {
        if (status == null) return "NEW";
        return switch (status) {
            case "PAYED", "PAYMENT_WAITING" -> "NEW";
            case "PRODUCT_PREPARE", "DELIVERING_HOLD" -> "CONFIRMED";
            case "DELIVERING" -> "SHIPPING";
            case "DELIVERED" -> "DELIVERED";
            case "CANCELED", "CANCEL_DONE" -> "CANCELED";
            case "RETURN_REQUEST", "RETURN_DONE" -> "RETURNED";
            case "EXCHANGE_REQUEST", "EXCHANGE_DONE" -> "EXCHANGED";
            default -> "CONFIRMED";
        };
    }

    /**
     * 날짜 파싱
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateStr, DATE_FORMAT);
        } catch (Exception e) {
            try {
                // 다른 포맷 시도
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
            } catch (Exception e2) {
                return LocalDateTime.now();
            }
        }
    }

    /**
     * 주소 빌드 (delivery 노드용)
     */
    private String buildDeliveryAddress(JsonNode delivery) {
        String baseAddr = delivery.path("baseAddress").asText("");
        String detailAddr = delivery.path("detailedAddress").asText("");
        return (baseAddr + " " + detailAddr).trim();
    }
}
