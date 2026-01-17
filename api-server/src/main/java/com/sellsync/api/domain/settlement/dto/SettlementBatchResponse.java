package com.sellsync.api.domain.settlement.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.entity.SettlementBatch;
import com.sellsync.api.domain.settlement.enums.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 정산 배치 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementBatchResponse {

    private UUID settlementBatchId;
    private UUID tenantId;
    private Marketplace marketplace;
    private String settlementCycle;
    
    private LocalDate settlementPeriodStart;
    private LocalDate settlementPeriodEnd;
    
    private SettlementStatus settlementStatus;
    
    private Integer totalOrderCount;
    private BigDecimal grossSalesAmount;
    private BigDecimal totalCommissionAmount;
    private BigDecimal totalPgFeeAmount;
    private BigDecimal totalShippingCharged;
    private BigDecimal totalShippingSettled;
    private BigDecimal expectedPayoutAmount;
    private BigDecimal actualPayoutAmount;
    private BigDecimal netPayoutAmount;
    
    private UUID commissionPostingId;
    private UUID receiptPostingId;
    
    private String marketplaceSettlementId;
    
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    
    private LocalDateTime collectedAt;
    private LocalDateTime validatedAt;
    private LocalDateTime postedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SettlementBatchResponse from(SettlementBatch batch) {
        return SettlementBatchResponse.builder()
                .settlementBatchId(batch.getSettlementBatchId())
                .tenantId(batch.getTenantId())
                .marketplace(batch.getMarketplace())
                .settlementCycle(batch.getSettlementCycle())
                .settlementPeriodStart(batch.getSettlementPeriodStart())
                .settlementPeriodEnd(batch.getSettlementPeriodEnd())
                .settlementStatus(batch.getSettlementStatus())
                .totalOrderCount(batch.getTotalOrderCount())
                .grossSalesAmount(batch.getGrossSalesAmount())
                .totalCommissionAmount(batch.getTotalCommissionAmount())
                .totalPgFeeAmount(batch.getTotalPgFeeAmount())
                .totalShippingCharged(batch.getTotalShippingCharged())
                .totalShippingSettled(batch.getTotalShippingSettled())
                .expectedPayoutAmount(batch.getExpectedPayoutAmount())
                .actualPayoutAmount(batch.getActualPayoutAmount())
                .netPayoutAmount(batch.getNetPayoutAmount())
                .commissionPostingId(batch.getCommissionPostingId())
                .receiptPostingId(batch.getReceiptPostingId())
                .marketplaceSettlementId(batch.getMarketplaceSettlementId())
                .attemptCount(batch.getAttemptCount())
                .nextRetryAt(batch.getNextRetryAt())
                .lastErrorCode(batch.getLastErrorCode())
                .lastErrorMessage(batch.getLastErrorMessage())
                .collectedAt(batch.getCollectedAt())
                .validatedAt(batch.getValidatedAt())
                .postedAt(batch.getPostedAt())
                .closedAt(batch.getClosedAt())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }
}
