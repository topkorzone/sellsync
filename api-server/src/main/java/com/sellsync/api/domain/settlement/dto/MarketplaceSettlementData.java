package com.sellsync.api.domain.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 마켓플레이스 정산 데이터 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceSettlementData {

    private String settlementId;
    private String marketplace;
    private String settlementCycle;
    private LocalDate settlementPeriodStart;
    private LocalDate settlementPeriodEnd;
    
    private BigDecimal grossSalesAmount;
    private BigDecimal totalCommissionAmount;
    private BigDecimal totalPgFeeAmount;
    private BigDecimal totalShippingCharged;
    private BigDecimal totalShippingSettled;
    private BigDecimal expectedPayoutAmount;
    private BigDecimal actualPayoutAmount;
    
    private List<SettlementOrderData> orders;
    
    private String rawPayload;  // JSON 원본 데이터

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SettlementOrderData {
        private String orderId;
        private String marketplaceOrderId;
        private BigDecimal grossSalesAmount;
        private BigDecimal commissionAmount;
        private BigDecimal pgFeeAmount;
        private BigDecimal shippingFeeCharged;
        private BigDecimal shippingFeeSettled;
        private BigDecimal netPayoutAmount;
    }
}
