package com.sellsync.api.domain.shipping.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 마켓 푸시 생성 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMarketPushRequestDto {

    @NotNull(message = "tenantId는 필수입니다")
    private UUID tenantId;

    @NotNull(message = "orderId는 필수입니다")
    private UUID orderId;

    @NotBlank(message = "trackingNo는 필수입니다")
    private String trackingNo;

    @NotNull(message = "marketplace는 필수입니다")
    private Marketplace marketplace;

    @NotBlank(message = "marketplaceOrderId는 필수입니다")
    private String marketplaceOrderId;

    @NotBlank(message = "carrierCode는 필수입니다")
    private String carrierCode;

    private String traceId;

    private UUID jobId;

    private String requestPayload;
}
