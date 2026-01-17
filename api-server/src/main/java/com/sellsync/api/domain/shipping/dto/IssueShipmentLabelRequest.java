package com.sellsync.api.domain.shipping.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 송장 발급 요청 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueShipmentLabelRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private Marketplace marketplace;

    @NotNull
    private String marketplaceOrderId;

    @NotNull
    private String carrierCode;

    // Optional: 내부 주문 ID (조회 최적화용)
    private UUID orderId;

    // Optional: 택배사 API 요청 페이로드
    private String requestPayload;

    // Optional: 추적 정보
    private String traceId;
    private UUID jobId;
}
