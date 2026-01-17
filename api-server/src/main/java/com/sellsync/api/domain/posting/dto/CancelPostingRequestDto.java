package com.sellsync.api.domain.posting.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 취소 전표 생성 요청 DTO
 * 
 * POST /api/orders/{orderId}/erp/cancel
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelPostingRequestDto {

    /**
     * 취소 유형
     * - FULL: 전체 취소
     * - PARTIAL: 부분 취소 (canceledItems 필수)
     */
    @NotNull(message = "cancelType은 필수입니다")
    private CancelType cancelType;

    /**
     * 부분 취소 시 취소된 상품 목록
     * [{ orderItemId, canceledQuantity, canceledAmount }]
     */
    private List<CanceledItem> canceledItems;

    /**
     * 배송비 환불 여부
     */
    @Builder.Default
    private Boolean refundShipping = false;

    /**
     * 취소 사유
     */
    private String reason;

    /**
     * 취소 유형
     */
    public enum CancelType {
        FULL("전체취소"),
        PARTIAL("부분취소");

        private final String displayName;

        CancelType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 취소된 상품 아이템
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CanceledItem {
        
        @NotNull(message = "orderItemId는 필수입니다")
        private UUID orderItemId;

        @NotNull(message = "canceledQuantity는 필수입니다")
        private Integer canceledQuantity;

        @NotNull(message = "canceledAmount는 필수입니다")
        private BigDecimal canceledAmount;
    }
}
