package com.sellsync.api.domain.shipping.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 송장 발급 요청 DTO (컨트롤러용)
 * 
 * POST /api/orders/{orderId}/shipments
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueShipmentRequestDto {

    @NotNull(message = "marketplace는 필수입니다")
    private Marketplace marketplace;

    @NotBlank(message = "marketplaceOrderId는 필수입니다")
    private String marketplaceOrderId;

    @NotBlank(message = "carrierCode는 필수입니다")
    private String carrierCode;

    /**
     * 택배사 API 요청 페이로드 (선택)
     * 예: 수령인 정보, 배송 메모 등
     */
    private String requestPayload;

    /**
     * 추적 ID (선택, 분산 추적용)
     */
    private String traceId;
}
