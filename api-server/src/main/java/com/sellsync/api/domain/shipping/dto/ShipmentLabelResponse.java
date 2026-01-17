package com.sellsync.api.domain.shipping.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.entity.ShipmentLabel;
import com.sellsync.api.domain.shipping.enums.ShipmentLabelStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 송장 발급 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentLabelResponse {

    private UUID shipmentLabelId;
    private UUID tenantId;
    private Marketplace marketplace;
    private String marketplaceOrderId;
    private String carrierCode;
    private UUID orderId;
    private String trackingNo;
    private ShipmentLabelStatus labelStatus;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String traceId;
    private UUID jobId;
    private LocalDateTime issuedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ShipmentLabelResponse from(ShipmentLabel entity) {
        return ShipmentLabelResponse.builder()
                .shipmentLabelId(entity.getShipmentLabelId())
                .tenantId(entity.getTenantId())
                .marketplace(entity.getMarketplace())
                .marketplaceOrderId(entity.getMarketplaceOrderId())
                .carrierCode(entity.getCarrierCode())
                .orderId(entity.getOrderId())
                .trackingNo(entity.getTrackingNo())
                .labelStatus(entity.getLabelStatus())
                .lastErrorCode(entity.getLastErrorCode())
                .lastErrorMessage(entity.getLastErrorMessage())
                .traceId(entity.getTraceId())
                .jobId(entity.getJobId())
                .issuedAt(entity.getIssuedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
