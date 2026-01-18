package com.sellsync.api.domain.settlement.adapter;

import com.sellsync.api.domain.settlement.dto.MarketplaceSettlementData;
import com.sellsync.api.domain.settlement.dto.smartstore.DailySettlementElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 쿠팡 정산 클라이언트 (Mock 구현)
 * 
 * 실제 구현 시:
 * - 쿠팡 파트너스 API (https://api-gateway.coupang.com)
 * - HMAC 서명 인증
 * - 정산 조회 API: GET /v2/providers/settlement_service/apis/api/v1/settlements
 */
@Slf4j
@Component
public class CoupangSettlementClient implements MarketplaceSettlementClient {

    @Override
    public String getMarketplaceCode() {
        return "COUPANG";
    }

    @Override
    public List<MarketplaceSettlementData> fetchSettlements(LocalDate startDate, 
                                                            LocalDate endDate, 
                                                            String credentials) {
        log.info("[Mock] 쿠팡 정산 데이터 수집: {} ~ {}", startDate, endDate);

        List<MarketplaceSettlementData> settlements = new ArrayList<>();

        // Mock 데이터 생성 (월별 정산)
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            MarketplaceSettlementData settlement = generateMockSettlement(current);
            settlements.add(settlement);
            current = current.plusMonths(1);
        }

        log.info("[Mock] 쿠팡 정산 데이터 수집 완료: {} 건", settlements.size());

        return settlements;
    }

    @Override
    public List<DailySettlementElement> fetchSettlementElements(LocalDate startDate, 
                                                                LocalDate endDate, 
                                                                String credentials) {
        log.info("[Mock] 쿠팡 정산 요소 수집: {} ~ {}", startDate, endDate);
        
        // 쿠팡은 아직 구현되지 않았으므로 빈 리스트 반환
        log.warn("[Mock] 쿠팡 정산 요소 수집은 아직 구현되지 않았습니다.");
        
        return new ArrayList<>();
    }

    @Override
    public MarketplaceSettlementData fetchSettlement(String settlementId, String credentials) {
        log.info("[Mock] 쿠팡 정산 상세 조회: settlementId={}", settlementId);

        return generateMockSettlement(LocalDate.now());
    }

    @Override
    public boolean testConnection(String credentials) {
        log.info("[Mock] 쿠팡 정산 API 연결 테스트");
        return true;
    }

    @Override
    public Integer getRemainingQuota() {
        // Mock: 쿠팡은 일일 API 호출 제한 500회
        return 480;
    }

    // ========== Mock Helper ==========

    private MarketplaceSettlementData generateMockSettlement(LocalDate startDate) {
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);
        String settlementCycle = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // Mock 주문 데이터 생성 (10건)
        List<MarketplaceSettlementData.SettlementOrderData> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
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
                .settlementId("COUPANG-SETTLE-" + settlementCycle)
                .marketplace("COUPANG")
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
        BigDecimal grossSales = BigDecimal.valueOf(80000 + (index * 5000));
        BigDecimal commission = grossSales.multiply(BigDecimal.valueOf(0.12)); // 12% 수수료
        BigDecimal pgFee = grossSales.multiply(BigDecimal.valueOf(0.03));      // 3% PG 수수료
        BigDecimal shippingCharged = BigDecimal.valueOf(2500);
        BigDecimal shippingSettled = BigDecimal.valueOf(2500);
        BigDecimal netPayout = grossSales.subtract(commission).subtract(pgFee);

        return MarketplaceSettlementData.SettlementOrderData.builder()
                .orderId(UUID.randomUUID().toString())
                .marketplaceOrderId("COUPANG-ORDER-" + UUID.randomUUID().toString().substring(0, 8))
                .grossSalesAmount(grossSales)
                .commissionAmount(commission)
                .pgFeeAmount(pgFee)
                .shippingFeeCharged(shippingCharged)
                .shippingFeeSettled(shippingSettled)
                .netPayoutAmount(netPayout)
                .build();
    }
}
