package com.sellsync.api.infra.marketplace.coupang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.order.client.MarketplaceOrderClient;
import com.sellsync.api.domain.order.dto.MarketplaceOrderDto;
import com.sellsync.api.domain.order.dto.MarketplaceOrderItemDto;
import com.sellsync.api.domain.order.enums.Marketplace;
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
     * 특정 상태의 주문 조회
     */
    private List<MarketplaceOrderDto> fetchOrdersByStatus(
            CoupangCredentials creds, LocalDateTime from, LocalDateTime to, String status) {
        
        String path = ORDERS_PATH.replace("{vendorId}", creds.getVendorId());
        String query = String.format("createdAtFrom=%s&createdAtTo=%s&status=%s",
                from.format(DATE_FORMAT),
                to.format(DATE_FORMAT),
                status);

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
            ResponseEntity<String> response = restTemplate.exchange(
                    url, 
                    HttpMethod.GET, 
                    request, 
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOrdersResponse(response.getBody());
            }
            return new ArrayList<>();
            
        } catch (HttpClientErrorException e) {
            log.error("[Coupang] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Coupang API error: " + e.getMessage(), e);
        }
    }

    /**
     * API 응답 파싱
     */
    private List<MarketplaceOrderDto> parseOrdersResponse(String responseBody) {
        List<MarketplaceOrderDto> orders = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataNode = root.path("data");
            
            if (dataNode.isArray()) {
                for (JsonNode orderNode : dataNode) {
                    orders.add(convertOrder(orderNode));
                }
            }
        } catch (Exception e) {
            log.error("[Coupang] Failed to parse response", e);
        }
        
        return orders;
    }

    /**
     * 쿠팡 주문 JSON → 통합 DTO 변환
     */
    private MarketplaceOrderDto convertOrder(JsonNode node) {
        List<MarketplaceOrderItemDto> items = convertItems(node.path("orderItems"));
        long totalAmount = items.stream()
                .mapToLong(i -> i.getLineAmount() != null ? i.getLineAmount() : 0L)
                .sum();

        return MarketplaceOrderDto.builder()
                .rawPayload(node.toString())
                .marketplaceOrderId(String.valueOf(node.path("orderId").asLong()))
                .bundleOrderId(String.valueOf(node.path("shipmentBoxId").asLong()))
                .orderStatus(mapStatus(node.path("status").asText()))
                .orderedAt(parseDateTime(node.path("orderedAt").asText()))
                .paidAt(parseDateTime(node.path("paidAt").asText()))
                // 주문자
                .buyerName(node.path("orderer").path("name").asText(""))
                .buyerPhone(node.path("orderer").path("email").asText(null)) // 쿠팡은 이메일만 제공
                // 수취인
                .receiverName(node.path("receiver").path("name").asText(""))
                .receiverPhone1(node.path("receiver").path("receiverPhoneNumber1").asText(null))
                .receiverPhone2(node.path("receiver").path("safeNumber").asText(null))
                .receiverZipCode(node.path("receiver").path("postCode").asText(null))
                .receiverAddress(buildAddress(node.path("receiver")))
                .safeNumber(node.path("receiver").path("safeNumber").asText(null))
                // 금액
                .totalPaidAmount(totalAmount)
                // 배송비
                .shippingFee(node.path("shippingPrice").asLong(0))
                .additionalShippingFee(node.path("remoteAreaExtraDeliveryCharge").asLong(0))
                // 기타
                .deliveryRequest(node.path("parcelPrintMessage").asText(null))
                .personalCustomsCode(node.path("overseaShippingInfoDto").path("personalCustomsClearanceCode").asText(null))
                // 상품
                .items(items)
                .build();
    }

    /**
     * 주문 상품 변환
     */
    private List<MarketplaceOrderItemDto> convertItems(JsonNode itemsNode) {
        List<MarketplaceOrderItemDto> items = new ArrayList<>();
        
        if (itemsNode.isArray()) {
            for (JsonNode item : itemsNode) {
                items.add(MarketplaceOrderItemDto.builder()
                        .marketplaceProductId(String.valueOf(item.path("vendorItemId").asLong()))
                        .marketplaceSku(String.valueOf(item.path("vendorItemId").asLong()))
                        .productName(item.path("vendorItemName").asText(""))
                        .exposedProductName(item.path("sellerProductName").asText(null))
                        .optionName(item.path("sellerProductItemName").asText(null))
                        .quantity(item.path("shippingCount").asInt(1))
                        .unitPrice(item.path("unitPrice").asLong(0))
                        .lineAmount(item.path("orderPrice").asLong(0))
                        .rawPayload(item.toString())
                        .build());
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
}
