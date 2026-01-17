package com.sellsync.api.domain.settlement.adapter;

import com.sellsync.api.domain.settlement.dto.MarketplaceSettlementData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 네이버 스마트스토어 정산 클라이언트 (Mock 구현)
 * 
 * 실제 구현 시:
 * - 네이버 커머스 API (https://api.commerce.naver.com)
 * - OAuth 2.0 인증
 * - 정산 조회 API: GET /external/v1/pay-settle/settlements
 */
@Slf4j
@Component
public class NaverSmartStoreSettlementClient implements MarketplaceSettlementClient {

    @Override
    public String getMarketplaceCode() {
        return "NAVER_SMARTSTORE";
    }

    @Override
    public List<MarketplaceSettlementData> fetchSettlements(LocalDate startDate, 
                                                            LocalDate endDate, 
                                                            String credentials) {
        log.info("[Mock] 네이버 정산 데이터 수집: {} ~ {}", startDate, endDate);

        List<MarketplaceSettlementData> settlements = new ArrayList<>();

        // Mock 데이터 생성 (주차별 정산)
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            MarketplaceSettlementData settlement = generateMockSettlement(current);
            settlements.add(settlement);
            current = current.plusWeeks(1);
        }

        log.info("[Mock] 네이버 정산 데이터 수집 완료: {} 건", settlements.size());

        return settlements;
    }

    @Override
    public MarketplaceSettlementData fetchSettlement(String settlementId, String credentials) {
        log.info("[Mock] 네이버 정산 상세 조회: settlementId={}", settlementId);

        // Mock 데이터 반환
        return generateMockSettlement(LocalDate.now());
    }

    @Override
    public boolean testConnection(String credentials) {
        log.info("[Mock] 네이버 정산 API 연결 테스트");
        return true;
    }

    @Override
    public Integer getRemainingQuota() {
        // Mock: 네이버는 일일 API 호출 제한 1000회
        return 950;
    }

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
}
