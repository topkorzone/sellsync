package com.sellsync.api.domain.order.dto;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.enums.SettlementCollectionStatus;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 목록 조회 최적화를 위한 Projection DTO
 * 
 * N+1 쿼리 해결 및 네트워크 트래픽 감소를 위해
 * 필요한 필드만 선택하여 조회합니다.
 * 
 * 참고: @AllArgsConstructor 제거 - JPQL Constructor Expression을 위해 수동 생성자 사용
 */
@Data
@Builder
@NoArgsConstructor
public class OrderListProjection {
    private UUID orderId;
    private String marketplaceOrderId;
    private Marketplace marketplace;
    private OrderStatus orderStatus;
    private SettlementCollectionStatus settlementStatus;
    private Long totalPaidAmount;
    private Long commissionAmount;
    private Long shippingCommissionAmount;
    private Long expectedSettlementAmount;
    private LocalDateTime paidAt;
    private LocalDateTime orderedAt;
    
    /**
     * 생성자 (JPQL Constructor Expression용)
     */
    public OrderListProjection(
            UUID orderId,
            String marketplaceOrderId,
            Marketplace marketplace,
            OrderStatus orderStatus,
            SettlementCollectionStatus settlementStatus,
            Long totalPaidAmount,
            Long commissionAmount,
            Long shippingCommissionAmount,
            Long expectedSettlementAmount,
            LocalDateTime paidAt,
            LocalDateTime orderedAt
    ) {
        this.orderId = orderId;
        this.marketplaceOrderId = marketplaceOrderId;
        this.marketplace = marketplace;
        this.orderStatus = orderStatus;
        this.settlementStatus = settlementStatus;
        this.totalPaidAmount = totalPaidAmount;
        this.commissionAmount = commissionAmount;
        this.shippingCommissionAmount = shippingCommissionAmount;
        this.expectedSettlementAmount = expectedSettlementAmount;
        this.paidAt = paidAt;
        this.orderedAt = orderedAt;
    }
}
