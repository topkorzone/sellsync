package com.sellsync.api.domain.shipping.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.enums.MarketPushStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 마켓 푸시 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPushResponseDto {

    private UUID shipmentMarketPushId;
    private UUID tenantId;
    private UUID orderId;
    private String trackingNo;
    private Marketplace marketplace;
    private String marketplaceOrderId;
    private String carrierCode;
    private MarketPushStatus pushStatus;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String traceId;
    private UUID jobId;
    private LocalDateTime pushedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Entity -> DTO 변환
     */
    public static MarketPushResponseDto from(ShipmentMarketPush entity) {
        return MarketPushResponseDto.builder()
            .shipmentMarketPushId(entity.getShipmentMarketPushId())
            .tenantId(entity.getTenantId())
            .orderId(entity.getOrderId())
            .trackingNo(entity.getTrackingNo())
            .marketplace(entity.getMarketplace())
            .marketplaceOrderId(entity.getMarketplaceOrderId())
            .carrierCode(entity.getCarrierCode())
            .pushStatus(entity.getPushStatus())
            .attemptCount(entity.getAttemptCount())
            .nextRetryAt(entity.getNextRetryAt())
            .lastErrorCode(entity.getLastErrorCode())
            .lastErrorMessage(entity.getLastErrorMessage())
            .traceId(entity.getTraceId())
            .jobId(entity.getJobId())
            .pushedAt(entity.getPushedAt())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
