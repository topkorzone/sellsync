package com.sellsync.api.domain.settlement.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 정산 배치 생성 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSettlementBatchRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private Marketplace marketplace;

    @NotNull
    private String settlementCycle;

    @NotNull
    private LocalDate settlementPeriodStart;

    @NotNull
    private LocalDate settlementPeriodEnd;

    private String marketplaceSettlementId;

    private String marketplacePayload;
}
