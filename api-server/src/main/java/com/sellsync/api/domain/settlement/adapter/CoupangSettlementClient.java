package com.sellsync.api.domain.settlement.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.settlement.dto.MarketplaceSettlementData;
import com.sellsync.api.domain.settlement.dto.coupang.CoupangSettlementApiResponse;
import com.sellsync.api.domain.settlement.dto.coupang.CoupangSettlementDeliveryFee;
import com.sellsync.api.domain.settlement.dto.coupang.CoupangSettlementItem;
import com.sellsync.api.domain.settlement.dto.coupang.CoupangSettlementOrder;
import com.sellsync.api.domain.settlement.dto.smartstore.DailySettlementElement;
import com.sellsync.api.infra.marketplace.coupang.CoupangCredentials;
import com.sellsync.api.infra.marketplace.coupang.CoupangHmacGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 쿠팡 정산 클라이언트 (실제 API 연동)
 * 
 * API: GET https://api-gateway.coupang.com/v2/providers/settlement_service/apis/api/v1/settlements
 * - HMAC-SHA256 서명 인증
 * - 월별 정산 내역 조회
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoupangSettlementClient implements MarketplaceSettlementClient {

    private static final String BASE_URL = "https://api-gateway.coupang.com";
    // ✅ 쿠팡 매출내역 조회 API (revenue-history) 사용
    // - 매출인식일 기준 정산 가능한 판매 매출 내역 조회
    // - 기존: /v2/providers/settlement_service/apis/api/v1/settlements (404 에러)
    // - 수정: /v2/providers/openapi/apis/api/v1/revenue-history
    private static final String SETTLEMENT_PATH = "/v2/providers/openapi/apis/api/v1/revenue-history";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_PER_PAGE = 50;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CoupangHmacGenerator hmacGenerator;

    @Override
    public String getMarketplaceCode() {
        return "COUPANG";
    }

    @Override
    public List<MarketplaceSettlementData> fetchSettlements(LocalDate startDate, 
                                                            LocalDate endDate, 
                                                            String credentials) {
        // 날짜 로직: 파라미터가 없으면 "오늘-1일 - 1개월 ~ 오늘-1일" 사용
        LocalDate actualStartDate = startDate;
        LocalDate actualEndDate = endDate;
        
        if (actualStartDate == null || actualEndDate == null) {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            actualEndDate = yesterday;
            actualStartDate = yesterday.minusMonths(1);
        }
        
        log.info("[Coupang Settlement] 정산 데이터 수집 시작: {} ~ {}", actualStartDate, actualEndDate);

        // 인증 정보 파싱
        CoupangCredentials creds = CoupangCredentials.parse(credentials);
        
        // 전체 정산 데이터 조회 (페이징 처리)
        List<CoupangSettlementOrder> allOrders = fetchAllSettlements(creds, actualStartDate, actualEndDate);
        
        // 주문 데이터를 통합 DTO로 변환
        List<MarketplaceSettlementData> settlements = new ArrayList<>();
        for (CoupangSettlementOrder order : allOrders) {
            MarketplaceSettlementData converted = convertToMarketplaceSettlementData(order);
            if (converted != null) {
                settlements.add(converted);
            }
        }
        
        log.info("[Coupang Settlement] 정산 데이터 수집 완료: {} 건", settlements.size());

        return settlements;
    }

    @Override
    public List<DailySettlementElement> fetchSettlementElements(LocalDate startDate, 
                                                                LocalDate endDate, 
                                                                String credentials) {
        // 날짜 로직: 파라미터가 없으면 "오늘-1일 - 1개월 ~ 오늘-1일" 사용
        LocalDate actualStartDate = startDate;
        LocalDate actualEndDate = endDate;
        
        // if (actualStartDate == null || actualEndDate == null) {
        //     LocalDate yesterday = LocalDate.now().minusDays(1);
        //     actualEndDate = yesterday;
        //     actualStartDate = yesterday.minusMonths(1);
        // }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        actualEndDate = yesterday;
        actualStartDate = yesterday.minusMonths(1).plusDays(1);
        
        log.info("[Coupang Settlement] 정산 요소 수집 시작: {} ~ {}", actualStartDate, actualEndDate);
        
        // 인증 정보 파싱
        CoupangCredentials creds = CoupangCredentials.parse(credentials);
        
        // 전체 정산 데이터 조회 (페이징 처리)
        List<CoupangSettlementOrder> allOrders = fetchAllSettlements(creds, actualStartDate, actualEndDate);
        
        // 주문 데이터를 정산 요소로 변환
        // ✅ items 배열의 각 아이템마다 index를 추가하여 고유하게 식별
        List<DailySettlementElement> allElements = new ArrayList<>();
        for (CoupangSettlementOrder order : allOrders) {
            // 1. 상품 아이템 변환
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                int index = 0;
                for (CoupangSettlementItem item : order.getItems()) {
                    DailySettlementElement element = convertToSettlementElement(order, item, index);
                    allElements.add(element);
                    index++;
                }
                
                // 2. 배송비 정보 변환 (deliveryFee가 있고 수수료가 0보다 큰 경우)
                // ✅ 배송비는 첫 번째 상품의 productOrderId (orderId_vendorItemId_0)에 할당
                if (order.getDeliveryFee() != null) {
                    CoupangSettlementDeliveryFee deliveryFee = order.getDeliveryFee();
                    long totalFee = (deliveryFee.getFee() != null ? deliveryFee.getFee() : 0L) +
                                   (deliveryFee.getFeeVat() != null ? deliveryFee.getFeeVat() : 0L);
                    
                    if (totalFee > 0) {
                        // 첫 번째 상품 아이템 가져오기
                        CoupangSettlementItem firstItem = order.getItems().get(0);
                        DailySettlementElement deliveryElement = convertDeliveryFeeToSettlementElement(order, firstItem);
                        allElements.add(deliveryElement);
                        
                        log.debug("[Coupang Settlement] 배송비 수수료 추가 - orderId={}, vendorItemId={}, fee={}, feeVat={}, total={}", 
                                order.getOrderId(), firstItem.getVendorItemId(), deliveryFee.getFee(), deliveryFee.getFeeVat(), totalFee);
                    }
                }
            }
        }
        
        log.info("[Coupang Settlement] 정산 요소 수집 완료: {} 건", allElements.size());
        
        return allElements;
    }

    @Override
    public MarketplaceSettlementData fetchSettlement(String settlementId, String credentials) {
        log.info("[Coupang Settlement] 정산 상세 조회: settlementId={}", settlementId);

        // settlementId 형식: "COUPANG-{orderId}"
        // 예: "COUPANG-9100162375288"
        if (!settlementId.startsWith("COUPANG-")) {
            throw new IllegalArgumentException("유효하지 않은 settlementId 형식: " + settlementId);
        }
        
        // 기본 날짜 범위로 조회 후 필터링
        List<MarketplaceSettlementData> settlements = fetchSettlements(null, null, credentials);
        
        // settlementId가 일치하는 항목 찾기
        return settlements.stream()
                .filter(s -> settlementId.equals(s.getSettlementId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean testConnection(String credentials) {
        log.info("[Coupang Settlement] API 연결 테스트 시작");
        
        try {
            // 인증 정보 파싱
            CoupangCredentials creds = CoupangCredentials.parse(credentials);
            
            // 어제 날짜로 정산 데이터 조회 시도 (실제 API 호출)
            LocalDate yesterday = LocalDate.now().minusDays(1);
            CoupangSettlementApiResponse response = fetchSettlementsWithPaging(creds, yesterday, yesterday, null);
            
            if (response != null && response.getCode() != null && response.getCode() == 200) {
                log.info("[Coupang Settlement] API 연결 테스트 성공");
                return true;
            }
            
            log.warn("[Coupang Settlement] API 응답이 비정상입니다.");
            return false;
            
        } catch (Exception e) {
            log.error("[Coupang Settlement] API 연결 테스트 실패: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Integer getRemainingQuota() {
        // 쿠팡 API는 일일 호출 제한이 있을 수 있음
        // 실제 API에서는 응답 헤더에서 Rate Limit 정보를 추출해야 함
        // 현재는 고정값 반환 (실제 구현 시 헤더에서 추출)
        log.debug("[Coupang Settlement] Rate Limit 정보는 API 응답 헤더에서 확인 필요");
        return 480; // 쿠팡은 일일 API 호출 제한 500회
    }

    // ========== API Helper Methods ==========
    
    /**
     * 전체 정산 데이터 조회 (페이징 자동 처리)
     * 
     * @param credentials 쿠팡 인증 정보
     * @param startDate 매출인식일 시작
     * @param endDate 매출인식일 종료
     * @return 전체 주문 목록
     */
    private List<CoupangSettlementOrder> fetchAllSettlements(CoupangCredentials credentials, 
                                                              LocalDate startDate, 
                                                              LocalDate endDate) {
        List<CoupangSettlementOrder> allOrders = new ArrayList<>();
        String nextToken = null;
        boolean hasNext = true;
        int pageCount = 0;
        
        while (hasNext) {
            try {
                pageCount++;
                log.debug("[Coupang Settlement] 페이지 {} 조회 중...", pageCount);
                
                CoupangSettlementApiResponse response = fetchSettlementsWithPaging(
                    credentials, startDate, endDate, nextToken
                );
                
                if (response != null && response.getData() != null) {
                    allOrders.addAll(response.getData());
                    log.debug("[Coupang Settlement] 페이지 {} 조회 완료: {} 건 (누적: {} 건)", 
                        pageCount, response.getData().size(), allOrders.size());
                    
                    // 다음 페이지 확인
                    hasNext = Boolean.TRUE.equals(response.getHasNext());
                    nextToken = response.getNextToken();
                    
                    if (hasNext && (nextToken == null || nextToken.isEmpty())) {
                        log.warn("[Coupang Settlement] hasNext=true이지만 nextToken이 비어있습니다. 조회 중단");
                        break;
                    }
                } else {
                    hasNext = false;
                }
                
            } catch (Exception e) {
                log.error("[Coupang Settlement] 페이지 {} 조회 실패: {}", pageCount, e.getMessage(), e);
                break;
            }
        }
        
        log.info("[Coupang Settlement] 전체 {} 페이지, {} 건 조회 완료", pageCount, allOrders.size());
        return allOrders;
    }
    
    /**
     * 쿠팡 정산 API 호출 (페이징 지원)
     * 
     * @param credentials 쿠팡 인증 정보
     * @param startDate 매출인식일 시작 (recognitionDateFrom)
     * @param endDate 매출인식일 종료 (recognitionDateTo)
     * @param token 다음 페이지 토큰 (null이면 첫 페이지)
     * @return API 응답 DTO
     */
    private CoupangSettlementApiResponse fetchSettlementsWithPaging(CoupangCredentials credentials, 
                                                                     LocalDate startDate, 
                                                                     LocalDate endDate,
                                                                     String token) {
        String path = SETTLEMENT_PATH;
        
        // 쿼리 파라미터 구성
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("vendorId=").append(credentials.getVendorId());
        queryBuilder.append("&recognitionDateFrom=").append(startDate.format(DATE_FORMAT));
        queryBuilder.append("&recognitionDateTo=").append(endDate.format(DATE_FORMAT));
        queryBuilder.append("&maxPerPage=").append(MAX_PER_PAGE);
        
        // ✅ 쿠팡 revenue-history API: 첫 페이지는 token= (빈 값) 필수
        if (token != null && !token.isEmpty()) {
            queryBuilder.append("&token=").append(token);
        } else {
            queryBuilder.append("&token=");  // 빈 값으로 명시
        }
        
        String query = queryBuilder.toString();
        
        // HMAC 서명 생성
        String authorization = hmacGenerator.generateAuthorization(credentials, "GET", path, query);
        
        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authorization);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", java.util.UUID.randomUUID().toString());
        
        // URL 생성
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + path)
                .query(query)
                .build()
                .toUriString();
        
        log.debug("[Coupang Settlement] API Request URL: {}", url);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            @SuppressWarnings("null")
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("null")
                String responseBody = response.getBody();
                log.debug("[Coupang Settlement] API 응답 수신 (길이: {} bytes)", responseBody.length());
                
                // JSON 응답 파싱
                CoupangSettlementApiResponse apiResponse = objectMapper.readValue(
                        responseBody,
                        CoupangSettlementApiResponse.class
                );
                
                log.info("[Coupang Settlement] API 호출 성공: code={}, {} 건의 데이터, hasNext={}",
                        apiResponse.getCode(),
                        apiResponse.getData() != null ? apiResponse.getData().size() : 0,
                        apiResponse.getHasNext());
                
                return apiResponse;
            }
            
            log.warn("[Coupang Settlement] API 응답이 비어있습니다.");
            return CoupangSettlementApiResponse.builder()
                    .code(200)
                    .data(new ArrayList<>())
                    .hasNext(false)
                    .build();
            
        } catch (HttpClientErrorException e) {
            log.error("[Coupang Settlement] API 호출 실패: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            
            // 401 Unauthorized: 인증 실패
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("쿠팡 인증이 유효하지 않습니다. 인증 정보를 확인해주세요.", e);
            }
            
            // 429 Too Many Requests: Rate Limit
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new RuntimeException("쿠팡 API 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요.", e);
            }
            
            throw new RuntimeException("쿠팡 정산 데이터 조회 실패: " + e.getMessage(), e);
            
        } catch (Exception e) {
            log.error("[Coupang Settlement] API 처리 중 오류 발생", e);
            throw new RuntimeException("쿠팡 정산 데이터 처리 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 쿠팡 주문 데이터를 통합 DTO로 변환
     * 
     * @param order 쿠팡 정산 주문
     * @return 통합 정산 데이터
     */
    private MarketplaceSettlementData convertToMarketplaceSettlementData(CoupangSettlementOrder order) {
        if (order == null) {
            return null;
        }
        
        LocalDate recognitionDate = parseDate(order.getRecognitionDate());
        LocalDate settlementDate = parseDate(order.getSettlementDate());
        
        // 정산 ID: COUPANG-{orderId}
        String settlementId = "COUPANG-" + order.getOrderId();
        
        // 정산 주기: 매출인식일 기준 (yyyy-MM)
        String settlementCycle = recognitionDate != null ? 
                recognitionDate.format(DateTimeFormatter.ofPattern("yyyy-MM")) : "UNKNOWN";
        
        // 총 판매 금액 계산
        long totalSalesAmount = 0L;
        long totalCommission = 0L;
        if (order.getItems() != null) {
            for (CoupangSettlementItem item : order.getItems()) {
                if (item.getSaleAmount() != null) {
                    totalSalesAmount += item.getSaleAmount();
                }
                totalCommission += item.getTotalCommission();
            }
        }
        
        // 배송비 정산 금액
        long deliverySettlement = 0L;
        if (order.getDeliveryFee() != null && order.getDeliveryFee().getSettlementAmount() != null) {
            deliverySettlement = order.getDeliveryFee().getSettlementAmount();
        }
        
        // 최종 정산 금액 = 상품 정산 금액 합계 + 배송비 정산
        long totalSettlement = 0L;
        if (order.getItems() != null) {
            for (CoupangSettlementItem item : order.getItems()) {
                if (item.getSettlementAmount() != null) {
                    totalSettlement += item.getSettlementAmount();
                }
            }
        }
        totalSettlement += deliverySettlement;
        
        // 금액 변환 (Long -> BigDecimal)
        BigDecimal grossSales = BigDecimal.valueOf(totalSalesAmount);
        BigDecimal totalCommissionAmount = BigDecimal.valueOf(totalCommission);
        BigDecimal shippingSettled = BigDecimal.valueOf(deliverySettlement);
        BigDecimal netPayout = BigDecimal.valueOf(totalSettlement);
        
        // 주문 데이터 변환
        List<MarketplaceSettlementData.SettlementOrderData> orders = new ArrayList<>();
        if (order.getItems() != null) {
            for (CoupangSettlementItem item : order.getItems()) {
                orders.add(convertItemToOrder(order, item));
            }
        }
        
        return MarketplaceSettlementData.builder()
                .settlementId(settlementId)
                .marketplace("COUPANG")
                .settlementCycle(settlementCycle)
                .settlementPeriodStart(recognitionDate)
                .settlementPeriodEnd(recognitionDate)
                .grossSalesAmount(grossSales)
                .totalCommissionAmount(totalCommissionAmount)
                .totalPgFeeAmount(BigDecimal.ZERO) // 쿠팡은 PG 수수료가 서비스 수수료에 포함
                .totalShippingCharged(BigDecimal.ZERO)
                .totalShippingSettled(shippingSettled)
                .expectedPayoutAmount(netPayout)
                .actualPayoutAmount(settlementDate != null ? netPayout : BigDecimal.ZERO)
                .orders(orders)
                .rawPayload(convertToJsonString(order))
                .build();
    }
    
    /**
     * 쿠팡 정산 항목을 정산 요소로 변환
     * 
     * @param order 쿠팡 정산 주문
     * @param item 쿠팡 정산 상품 항목
     * @param index items 배열 내 아이템 인덱스
     * @return 정산 요소 (DailySettlementElement)
     */
    private DailySettlementElement convertToSettlementElement(CoupangSettlementOrder order, CoupangSettlementItem item, int index) {
        // 쿠팡은 saleType에 따라 정산 유형 결정
        String settleType = "SALE".equals(order.getSaleType()) ? "NORMAL" : 
                           "CANCEL".equals(order.getSaleType()) ? "CANCEL" : 
                           "RETURN".equals(order.getSaleType()) ? "RETURN" : "NORMAL";
        
        // ✅ 주문 수집과 동일한 형식으로 productOrderId 생성: orderId_vendorItemId_index
        String productOrderId = String.valueOf(order.getOrderId()) + "_" + item.getVendorItemId() + "_" + index;
        
        return DailySettlementElement.builder()
                .orderId(String.valueOf(order.getOrderId()))
                .productOrderId(productOrderId)
                .productOrderType("PROD_ORDER")
                .settleType(settleType)
                .productId(String.valueOf(item.getProductId()))
                .productName(item.getProductName())
                .payDate(order.getSaleDate())
                .settleBasisStartDate(order.getRecognitionDate())
                .settleBasisEndDate(order.getRecognitionDate())
                .settleExpectDate(order.getSettlementDate())
                .paySettleAmount(item.getSaleAmount() != null ? item.getSaleAmount() : 0L)
                .totalPayCommissionAmount(item.getServiceFee() != null ? item.getServiceFee() : 0L)
                .freeInstallmentCommissionAmount(0L)
                .sellingInterlockCommissionAmount(item.getCouranteeFee() != null ? item.getCouranteeFee() : 0L)
                .benefitSettleAmount(0L)
                .settleExpectAmount(item.getSettlementAmount() != null ? item.getSettlementAmount() : 0L)
                .shippingSettleAmount(0L) // 배송비는 주문 레벨에서 별도 처리
                .build();
    }
    
    /**
     * 쿠팡 배송비 정보를 정산 요소로 변환
     * 
     * ✅ 주의: orders 테이블의 bundleOrderId는 shipmentBoxId를 저장하고 있으나,
     *          정산 API에는 shipmentBoxId가 없으므로 매칭 불가
     * ✅ 해결: 배송비를 첫 번째 상품의 productOrderId (orderId_vendorItemId_0)에 할당
     * 
     * @param order 쿠팡 정산 주문
     * @param firstItem 첫 번째 상품 아이템 (productOrderId 생성용)
     * @return 배송비 정산 요소 (DailySettlementElement)
     */
    private DailySettlementElement convertDeliveryFeeToSettlementElement(CoupangSettlementOrder order, 
                                                                          CoupangSettlementItem firstItem) {
        CoupangSettlementDeliveryFee deliveryFee = order.getDeliveryFee();
        
        // 쿠팡은 saleType에 따라 정산 유형 결정
        String settleType = "SALE".equals(order.getSaleType()) ? "NORMAL" : 
                           "CANCEL".equals(order.getSaleType()) ? "CANCEL" : 
                           "RETURN".equals(order.getSaleType()) ? "RETURN" : "NORMAL";
        
        // 배송비 수수료 계산 (fee + feeVat)
        long deliveryCommission = (deliveryFee.getFee() != null ? deliveryFee.getFee() : 0L) +
                                 (deliveryFee.getFeeVat() != null ? deliveryFee.getFeeVat() : 0L);
        
        // 배송비 정산 금액
        long deliverySettlement = deliveryFee.getSettlementAmount() != null ? deliveryFee.getSettlementAmount() : 0L;
        
        // 배송비 금액
        long deliveryAmount = deliveryFee.getAmount() != null ? deliveryFee.getAmount() : 0L;
        
        // ✅ 배송비는 첫 번째 상품의 productOrderId (orderId_vendorItemId_0)에 매핑
        String productOrderId = String.valueOf(order.getOrderId()) + "_" + firstItem.getVendorItemId() + "_0";
        
        return DailySettlementElement.builder()
                .orderId(String.valueOf(order.getOrderId()))  // 정산 API의 orderId
                .productOrderId(productOrderId)  // ✅ 첫 번째 상품과 동일한 패턴 (orderId_vendorItemId_0)
                .productOrderType("DELIVERY")  // 배송비 타입
                .settleType(settleType)
                .productId("DELIVERY")
                .productName("배송비")
                .payDate(order.getSaleDate())
                .settleBasisStartDate(order.getRecognitionDate())
                .settleBasisEndDate(order.getRecognitionDate())
                .settleExpectDate(order.getSettlementDate())
                .paySettleAmount(deliveryAmount)  // 배송비 금액
                .totalPayCommissionAmount(deliveryCommission)  // 배송비 수수료 (fee + feeVat)
                .freeInstallmentCommissionAmount(0L)
                .sellingInterlockCommissionAmount(0L)
                .benefitSettleAmount(0L)
                .settleExpectAmount(deliverySettlement)  // 배송비 정산 금액
                .shippingSettleAmount(deliverySettlement)  // 배송비 정산 금액
                .build();
    }
    
    /**
     * 쿠팡 정산 항목을 주문 데이터로 변환
     * 
     * @param order 쿠팡 정산 주문
     * @param item 쿠팡 정산 상품 항목
     * @return 주문 데이터
     */
    private MarketplaceSettlementData.SettlementOrderData convertItemToOrder(CoupangSettlementOrder order, 
                                                                             CoupangSettlementItem item) {
        BigDecimal grossSales = item.getSaleAmount() != null ?
                BigDecimal.valueOf(item.getSaleAmount()) : BigDecimal.ZERO;
        
        BigDecimal commission = BigDecimal.valueOf(item.getTotalCommission());
        
        BigDecimal pgFee = BigDecimal.ZERO; // 쿠팡은 PG 수수료가 서비스 수수료에 포함
        
        BigDecimal shippingFee = BigDecimal.ZERO; // 배송비는 주문 레벨에서 별도 관리
        
        BigDecimal netPayout = item.getSettlementAmount() != null ?
                BigDecimal.valueOf(item.getSettlementAmount()) : BigDecimal.ZERO;
        
        return MarketplaceSettlementData.SettlementOrderData.builder()
                .orderId(String.valueOf(order.getOrderId()) + "-" + item.getVendorItemId())
                .marketplaceOrderId(String.valueOf(order.getOrderId()))
                .grossSalesAmount(grossSales)
                .commissionAmount(commission)
                .pgFeeAmount(pgFee)
                .shippingFeeCharged(shippingFee)
                .shippingFeeSettled(shippingFee)
                .netPayoutAmount(netPayout)
                .build();
    }
    
    /**
     * 날짜 문자열을 LocalDate로 변환
     * 
     * @param dateStr 날짜 문자열 (yyyy-MM-dd)
     * @return LocalDate
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMAT);
        } catch (Exception e) {
            log.warn("[Coupang Settlement] 날짜 파싱 실패: {}", dateStr);
            return null;
        }
    }
    
    /**
     * 객체를 JSON 문자열로 변환
     * 
     * @param obj 변환할 객체
     * @return JSON 문자열
     */
    private String convertToJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[Coupang Settlement] JSON 변환 실패", e);
            return "{}";
        }
    }
}
