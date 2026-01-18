package com.sellsync.api.domain.settlement.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.settlement.dto.MarketplaceSettlementData;
import com.sellsync.api.domain.settlement.dto.smartstore.DailySettlementApiResponse;
import com.sellsync.api.domain.settlement.dto.smartstore.DailySettlementElement;
import com.sellsync.api.infra.marketplace.smartstore.SmartStoreCredentials;
import com.sellsync.api.infra.marketplace.smartstore.SmartStoreTokenService;
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
 * 네이버 스마트스토어 정산 클라이언트 (실제 API 연동)
 * 
 * API: GET https://api.commerce.naver.com/external/v1/pay-settle/settle/case
 * - OAuth 2.0 Bearer Token 인증
 * - 결제일 기준 건별 정산 내역 조회
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverSmartStoreSettlementClient implements MarketplaceSettlementClient {

    private static final String BASE_URL = "https://api.commerce.naver.com";
    private static final String SETTLEMENT_ENDPOINT = "/external/v1/pay-settle/settle/case";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SmartStoreTokenService tokenService;

    @Override
    public String getMarketplaceCode() {
        return "NAVER_SMARTSTORE";
    }

    @Override
    public List<MarketplaceSettlementData> fetchSettlements(LocalDate startDate, 
                                                            LocalDate endDate, 
                                                            String credentials) {
        log.info("[SmartStore Settlement] 정산 데이터 수집 시작: {} ~ {}", startDate, endDate);

        // 인증 정보 파싱
        SmartStoreCredentials creds = SmartStoreCredentials.parse(credentials);
        
        // Access Token 발급
        String accessToken = tokenService.getAccessToken(creds);
        
        // 날짜별로 API 호출 (건별 정산 API는 날짜별로 호출해야 함)
        List<MarketplaceSettlementData> allSettlements = new ArrayList<>();
        LocalDate currentDate = startDate;
        
        while (!currentDate.isAfter(endDate)) {
            try {
                // API 호출하여 일별 정산 데이터 조회
                DailySettlementApiResponse apiResponse = fetchDailySettlements(accessToken, currentDate);
                
                // API 응답을 통합 DTO로 변환
                List<MarketplaceSettlementData> settlements = convertToMarketplaceSettlementData(apiResponse);
                allSettlements.addAll(settlements);
                
                log.debug("[SmartStore Settlement] 날짜 {} 정산 데이터: {} 건", currentDate, settlements.size());
            } catch (Exception e) {
                log.error("[SmartStore Settlement] 날짜 {} 정산 데이터 조회 실패: {}", currentDate, e.getMessage(), e);
            }
            
            currentDate = currentDate.plusDays(1);
        }
        
        log.info("[SmartStore Settlement] 정산 데이터 수집 완료: {} 건", allSettlements.size());

        return allSettlements;
    }

    @Override
    public List<DailySettlementElement> fetchSettlementElements(LocalDate startDate, 
                                                                LocalDate endDate, 
                                                                String credentials) {
        log.info("[SmartStore Settlement] 정산 요소 수집 시작: {} ~ {}", startDate, endDate);

        // 인증 정보 파싱
        SmartStoreCredentials creds = SmartStoreCredentials.parse(credentials);
        
        // Access Token 발급
        String accessToken = tokenService.getAccessToken(creds);
        
        // 날짜별로 API 호출하여 정산 요소 수집
        List<DailySettlementElement> allElements = new ArrayList<>();
        LocalDate currentDate = startDate;
        
        while (!currentDate.isAfter(endDate)) {
            try {
                // API 호출하여 일별 정산 데이터 조회
                DailySettlementApiResponse apiResponse = fetchDailySettlements(accessToken, currentDate);
                
                // API 응답에서 정산 요소 추출
                if (apiResponse != null && apiResponse.getElements() != null) {
                    allElements.addAll(apiResponse.getElements());
                    log.debug("[SmartStore Settlement] 날짜 {} 정산 요소: {} 건", 
                        currentDate, apiResponse.getElements().size());
                }
            } catch (Exception e) {
                log.error("[SmartStore Settlement] 날짜 {} 정산 요소 조회 실패: {}", currentDate, e.getMessage(), e);
            }
            
            currentDate = currentDate.plusDays(1);
        }
        
        log.info("[SmartStore Settlement] 정산 요소 수집 완료: {} 건", allElements.size());

        return allElements;
    }
    
    /**
     * 스마트스토어 건별 정산 API 호출
     * 
     * @param accessToken Bearer Token
     * @param searchDate 조회 날짜 (결제일 기준)
     * @return API 응답 DTO
     */
    @SuppressWarnings("null")
    private DailySettlementApiResponse fetchDailySettlements(String accessToken, LocalDate searchDate) {
        HttpHeaders headers = new HttpHeaders();
        String token = accessToken;
        headers.setBearerAuth(token);
        MediaType contentType = MediaType.APPLICATION_JSON;
        headers.setContentType(contentType);
        
        // URL 생성 (날짜 포맷: yyyy-MM-dd, periodType: SETTLE_CASEBYCASE_PAY_DATE)
        String urlString = UriComponentsBuilder.fromHttpUrl(BASE_URL + SETTLEMENT_ENDPOINT)
                .queryParam("searchDate", searchDate.format(DATE_FORMAT))
                .queryParam("periodType", "SETTLE_CASEBYCASE_PAY_DATE")
                .build()
                .toUriString();
        
        log.debug("[SmartStore Settlement] API Request URL: {}", urlString);
        
        // URI 객체로 변환
        @SuppressWarnings("null")
        java.net.URI uri = java.net.URI.create(urlString);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            @SuppressWarnings("null")
            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // ✅ 디버깅: 실제 API 응답 로깅
                String responseBody = response.getBody();
                log.info("[SmartStore Settlement] ==================== API 응답 시작 ====================");
                log.info("[SmartStore Settlement] API 응답 전체: {}", responseBody);
                log.info("[SmartStore Settlement] ==================== API 응답 끝 ====================");
                
                // JSON 응답 파싱
                DailySettlementApiResponse apiResponse = objectMapper.readValue(
                        responseBody, 
                        DailySettlementApiResponse.class
                );
                
                log.info("[SmartStore Settlement] API 호출 성공: {} 건의 정산 데이터 수신", 
                        apiResponse.getElements() != null ? apiResponse.getElements().size() : 0);
                
                // ✅ 디버깅: 첫 번째 element 상세 로깅
                if (apiResponse.getElements() != null && !apiResponse.getElements().isEmpty()) {
                    DailySettlementElement firstElement = apiResponse.getElements().get(0);
                    log.info("[SmartStore Settlement] 🔍 첫 번째 element 상세:");
                    log.info("  - settleBasisStartDate: {}", firstElement.getSettleBasisStartDate());
                    log.info("  - settleBasisEndDate: {}", firstElement.getSettleBasisEndDate());
                    log.info("  - settleExpectDate: {}", firstElement.getSettleExpectDate());
                    log.info("  - settleAmount: {}", firstElement.getSettleAmount());
                    log.info("  - commissionAmount: {}", firstElement.getCommissionAmount());
                }
                
                return apiResponse;
            }
            
            log.warn("[SmartStore Settlement] API 응답이 비어있습니다.");
            return DailySettlementApiResponse.builder()
                    .elements(new ArrayList<>())
                    .build();
            
        } catch (HttpClientErrorException e) {
            log.error("[SmartStore Settlement] API 호출 실패: {} - {}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            
            // 401 Unauthorized: 토큰 만료 또는 인증 실패
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("스마트스토어 인증이 만료되었거나 유효하지 않습니다. 재인증이 필요합니다.", e);
            }
            
            // 429 Too Many Requests: Rate Limit
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new RuntimeException("스마트스토어 API 호출 한도를 초과했습니다. 잠시 후 다시 시도해주세요.", e);
            }
            
            throw new RuntimeException("스마트스토어 정산 데이터 조회 실패: " + e.getMessage(), e);
            
        } catch (Exception e) {
            log.error("[SmartStore Settlement] API 처리 중 오류 발생", e);
            throw new RuntimeException("스마트스토어 정산 데이터 처리 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * API 응답을 통합 DTO로 변환
     * 
     * @param apiResponse 스마트스토어 API 응답
     * @return 통합 정산 데이터 리스트
     */
    private List<MarketplaceSettlementData> convertToMarketplaceSettlementData(DailySettlementApiResponse apiResponse) {
        if (apiResponse == null || apiResponse.getElements() == null || apiResponse.getElements().isEmpty()) {
            log.warn("[SmartStore Settlement] 변환할 정산 데이터가 없습니다.");
            return new ArrayList<>();
        }
        
        List<MarketplaceSettlementData> settlements = new ArrayList<>();
        
        for (DailySettlementElement element : apiResponse.getElements()) {
            try {
                MarketplaceSettlementData settlement = convertElement(element);
                settlements.add(settlement);
            } catch (Exception e) {
                log.warn("[SmartStore Settlement] 정산 데이터 변환 실패: {}", e.getMessage(), e);
            }
        }
        
        return settlements;
    }
    
    /**
     * DailySettlementElement를 MarketplaceSettlementData로 변환
     * 
     * @param element 스마트스토어 정산 요소
     * @return 통합 정산 데이터
     */
    private MarketplaceSettlementData convertElement(DailySettlementElement element) {
        LocalDate startDate = element.getSettleBasisStartDateAsLocalDate();
        LocalDate endDate = element.getSettleBasisEndDateAsLocalDate();
        
        // 정산 주기 ID 생성 (예: 2026-W03)
        String settlementCycle = startDate != null ? 
                startDate.format(DateTimeFormatter.ofPattern("yyyy-'W'ww")) : "UNKNOWN";
        
        // 정산 ID 생성
        String settlementId = "NAVER-SETTLE-" + element.getSettleBasisStartDate() + "-" + element.getSettleBasisEndDate();
        
        // ✅ 금액 변환 (Long -> BigDecimal) - 새로운 필드 사용
        // calculatedSettleAmount: 실제 정산 금액
        BigDecimal settleAmount = element.getCalculatedSettleAmount() != null ? 
                BigDecimal.valueOf(element.getCalculatedSettleAmount()) : BigDecimal.ZERO;
        
        // totalCommission: 총 수수료
        BigDecimal commissionAmount = BigDecimal.valueOf(element.getTotalCommission());
        
        // paySettleAmount: 결제 금액 (총 매출액)
        BigDecimal grossSales = element.getPaySettleAmount() != null ?
                BigDecimal.valueOf(element.getPaySettleAmount()) : BigDecimal.ZERO;
        
        BigDecimal shippingSettleAmount = element.getShippingSettleAmount() != null ? 
                BigDecimal.valueOf(element.getShippingSettleAmount()) : BigDecimal.ZERO;
        BigDecimal benefitSettleAmount = element.getBenefitSettleAmount() != null ? 
                BigDecimal.valueOf(element.getBenefitSettleAmount()) : BigDecimal.ZERO;
        
        // 실제 정산 금액 = 정산금액 + 배송비 정산 + 혜택 정산
        BigDecimal netPayout = settleAmount.add(shippingSettleAmount).add(benefitSettleAmount);
        
        return MarketplaceSettlementData.builder()
                .settlementId(settlementId)
                .marketplace("NAVER_SMARTSTORE")
                .settlementCycle(settlementCycle)
                .settlementPeriodStart(startDate)
                .settlementPeriodEnd(endDate)
                .grossSalesAmount(grossSales)
                .totalCommissionAmount(commissionAmount)
                .totalPgFeeAmount(BigDecimal.ZERO) // 스마트스토어 API에는 PG 수수료가 별도로 없음 (수수료에 포함)
                .totalShippingCharged(BigDecimal.ZERO) // 일별 정산 API에는 청구된 배송비 정보 없음
                .totalShippingSettled(shippingSettleAmount)
                .expectedPayoutAmount(netPayout)
                .actualPayoutAmount(element.isSettlementCompleted() ? netPayout : BigDecimal.ZERO)
                .orders(new ArrayList<>()) // 일별 정산 API에는 주문 상세 정보 없음
                .rawPayload(convertToJsonString(element))
                .build();
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
            log.warn("[SmartStore Settlement] JSON 변환 실패", e);
            return "{}";
        }
    }

    @Override
    public MarketplaceSettlementData fetchSettlement(String settlementId, String credentials) {
        log.info("[SmartStore Settlement] 정산 상세 조회: settlementId={}", settlementId);

        // settlementId 형식: "NAVER-SETTLE-yyyyMMdd-yyyyMMdd"
        // 예: "NAVER-SETTLE-20260115-20260115"
        String[] parts = settlementId.split("-");
        if (parts.length < 4) {
            throw new IllegalArgumentException("유효하지 않은 settlementId 형식: " + settlementId);
        }
        
        String startDateStr = parts[2];
        String endDateStr = parts[3];
        
        LocalDate startDate = LocalDate.parse(startDateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDate endDate = LocalDate.parse(endDateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 해당 날짜 범위로 정산 데이터 조회
        List<MarketplaceSettlementData> settlements = fetchSettlements(startDate, endDate, credentials);
        
        // 첫 번째 결과 반환
        return settlements.isEmpty() ? null : settlements.get(0);
    }

    @Override
    public boolean testConnection(String credentials) {
        log.info("[SmartStore Settlement] API 연결 테스트 시작");
        
        try {
            // 인증 정보 파싱
            SmartStoreCredentials creds = SmartStoreCredentials.parse(credentials);
            
            // Access Token 발급 시도
            String accessToken = tokenService.getAccessToken(creds);
            
            if (accessToken != null && !accessToken.isEmpty()) {
                log.info("[SmartStore Settlement] API 연결 테스트 성공");
                return true;
            }
            
            log.warn("[SmartStore Settlement] Access Token이 비어있습니다.");
            return false;
            
        } catch (Exception e) {
            log.error("[SmartStore Settlement] API 연결 테스트 실패: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Integer getRemainingQuota() {
        // 네이버 스마트스토어 API는 일일 호출 제한이 있을 수 있음
        // 실제 API에서는 응답 헤더에서 Rate Limit 정보를 추출해야 함
        // 현재는 고정값 반환 (실제 구현 시 헤더에서 추출)
        log.debug("[SmartStore Settlement] Rate Limit 정보는 API 응답 헤더에서 확인 필요");
        return null; // null 반환 시 무제한으로 간주
    }

    /* ===== Mock 코드 (주석 처리) =====
     * 
     * 실제 API 연동으로 대체되었습니다.
     * 필요 시 참고용으로 보존합니다.
     */

    /*
    // ========== Mock Helper ==========

    private MarketplaceSettlementData generateMockSettlement(LocalDate startDate) {
        LocalDate endDate = startDate.plusDays(6);
        String settlementCycle = startDate.format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));

        // Mock 주문 데이터 생성 (5건)
        List<MarketplaceSettlementData.SettlementOrderData> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            orders.add(generateMockOrder(i));
        }

        // 집계 금액 계산
        BigDecimal grossSales = orders.stream()
                .map(MarketplaceSettlementData.SettlementOrderData::getGrossSalesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCommission = orders.stream()
                .map(MarketplaceSettlementData.SettlementOrderData::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPgFee = orders.stream()
                .map(MarketplaceSettlementData.SettlementOrderData::getPgFeeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalShippingCharged = orders.stream()
                .map(MarketplaceSettlementData.SettlementOrderData::getShippingFeeCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalShippingSettled = orders.stream()
                .map(MarketplaceSettlementData.SettlementOrderData::getShippingFeeSettled)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPayout = grossSales
                .subtract(totalCommission)
                .subtract(totalPgFee)
                .add(totalShippingSettled.subtract(totalShippingCharged));

        return MarketplaceSettlementData.builder()
                .settlementId("NAVER-SETTLE-" + settlementCycle)
                .marketplace("NAVER_SMARTSTORE")
                .settlementCycle(settlementCycle)
                .settlementPeriodStart(startDate)
                .settlementPeriodEnd(endDate)
                .grossSalesAmount(grossSales)
                .totalCommissionAmount(totalCommission)
                .totalPgFeeAmount(totalPgFee)
                .totalShippingCharged(totalShippingCharged)
                .totalShippingSettled(totalShippingSettled)
                .expectedPayoutAmount(netPayout)
                .actualPayoutAmount(netPayout)
                .orders(orders)
                .rawPayload("{\"mock\":true}")
                .build();
    }

    private MarketplaceSettlementData.SettlementOrderData generateMockOrder(int index) {
        BigDecimal grossSales = BigDecimal.valueOf(50000 + (index * 10000));
        BigDecimal commission = grossSales.multiply(BigDecimal.valueOf(0.10)); // 10% 수수료
        BigDecimal pgFee = grossSales.multiply(BigDecimal.valueOf(0.02));      // 2% PG 수수료
        BigDecimal shippingCharged = BigDecimal.valueOf(3000);
        BigDecimal shippingSettled = BigDecimal.valueOf(3000);
        BigDecimal netPayout = grossSales.subtract(commission).subtract(pgFee);

        return MarketplaceSettlementData.SettlementOrderData.builder()
                .orderId(UUID.randomUUID().toString())
                .marketplaceOrderId("NAVER-ORDER-" + UUID.randomUUID().toString().substring(0, 8))
                .grossSalesAmount(grossSales)
                .commissionAmount(commission)
                .pgFeeAmount(pgFee)
                .shippingFeeCharged(shippingCharged)
                .shippingFeeSettled(shippingSettled)
                .netPayoutAmount(netPayout)
                .build();
    }
    */
}
