package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.enums.PostingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 전표 생성 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostingRequest {

    @NotNull(message = "tenantId는 필수입니다")
    private UUID tenantId;

    @NotBlank(message = "erpCode는 필수입니다")
    private String erpCode;

    @NotNull(message = "orderId는 필수입니다")
    private UUID orderId;

    @NotNull(message = "marketplace는 필수입니다")
    private Marketplace marketplace;

    @NotBlank(message = "marketplaceOrderId는 필수입니다")
    private String marketplaceOrderId;

    @NotNull(message = "postingType은 필수입니다")
    private PostingType postingType;

    /**
     * 원 전표 ID (취소전표인 경우)
     */
    private UUID originalPostingId;

    /**
     * ERP 전송용 요청 페이로드 (JSON)
     */
    private String requestPayload;
}
