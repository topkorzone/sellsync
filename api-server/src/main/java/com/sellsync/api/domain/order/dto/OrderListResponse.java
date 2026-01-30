package com.sellsync.api.domain.order.dto;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 목록 응답 DTO (간략 정보)
 * 
 * 주문 목록 조회 시 사용하는 경량 DTO
 * - 목록 화면에 필요한 정보 포함
 * 
 * 주의사항:
 * - shippingFee: 배송비만 표시 (배송비 자체의 금액)
 * - commissionAmount: 상품 판매 수수료만 표시 (배송비 수수료 제외)
 * - expectedSettlementAmount: 상품에 대한 정산 예정 금액만 표시 (배송비 정산 금액 제외)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderListResponse {

    private UUID orderId;
    private UUID tenantId;
    private UUID storeId;
    private Marketplace marketplace;
    private String marketplaceOrderId;
    private String bundleOrderId;
    private OrderStatus orderStatus;
    private LocalDateTime paidAt;  // 결재일 (주문일시에서 변경)
    
    // Customer
    private String buyerName;
    private String receiverName;
    private String receiverPhone1;
    
    // Payment
    private Long totalPaidAmount;
    private Long shippingFee;  // 배송비 (배송비 자체만, 배송비 수수료나 정산액 아님)
    private Long commissionAmount;  // 상품 판매 수수료 (배송비 수수료 제외)
    private Long expectedSettlementAmount;  // 상품 정산예정금액 (배송비 정산 금액 제외)
    
    // Order Items (간략 정보)
    private List<OrderItemSummary> items;
    
    // 매핑 상태 (MAPPED, UNMAPPED, PARTIAL)
    private String mappingStatus;
    
    // ERP 전표번호
    private String erpDocumentNo;

    // 전표 존재 여부 (READY 등 ERP 전송 전 상태 포함)
    private boolean hasPosting;
    
    // 송장번호
    private String trackingNo;
    
    // 택배사명
    private String carrierName;
    
    // 정산 수집 상태
    private String settlementStatus;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Order 엔티티로부터 목록용 DTO 생성
     * 
     * 주의: shippingFee, commissionAmount, expectedSettlementAmount는
     * 서비스 레이어에서 정산 테이블을 기반으로 별도 설정됨
     */
    public static OrderListResponse from(Order order) {
        // 배송비: totalShippingAmount 우선, 없으면 shippingFee 사용
        Long displayShippingFee = order.getTotalShippingAmount();
        if (displayShippingFee == null || displayShippingFee == 0) {
            displayShippingFee = order.getShippingFee();
        }
        
        return OrderListResponse.builder()
                .orderId(order.getOrderId())
                .tenantId(order.getTenantId())
                .storeId(order.getStoreId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .bundleOrderId(order.getBundleOrderId())
                .orderStatus(order.getOrderStatus())
                .paidAt(order.getPaidAt())  // 결재일로 변경
                .buyerName(order.getBuyerName())
                .receiverName(order.getReceiverName())
                .receiverPhone1(order.getReceiverPhone1())
                .totalPaidAmount(order.getTotalPaidAmount())
                .shippingFee(displayShippingFee)  // 배송비만 표시
                .commissionAmount(order.getCommissionAmount())  // 상품 수수료 (배송비 수수료 제외)
                .expectedSettlementAmount(null)  // 서비스에서 정산 테이블 기반으로 설정
                .items(order.getItems().stream()
                        .map(OrderItemSummary::from)
                        .collect(Collectors.toList()))
                .mappingStatus(null)  // 서비스에서 별도로 설정
                .erpDocumentNo(null)  // 서비스에서 별도로 설정
                .trackingNo(null)     // 서비스에서 별도로 설정
                .carrierName(null)    // 서비스에서 별도로 설정
                .settlementStatus(order.getSettlementStatus() != null ? order.getSettlementStatus().name() : "NOT_COLLECTED")
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
    
    /**
     * 주문 상품 요약 정보
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemSummary {
        private UUID orderItemId;
        private Integer lineNo;
        private String marketplaceProductId;
        private String marketplaceSku;
        private String productName;
        private String optionName;
        private Integer quantity;
        private Long unitPrice;
        private Long lineAmount;
        
        public static OrderItemSummary from(OrderItem item) {
            return OrderItemSummary.builder()
                    .orderItemId(item.getOrderItemId())
                    .lineNo(item.getLineNo())
                    .marketplaceProductId(item.getMarketplaceProductId())
                    .marketplaceSku(item.getMarketplaceSku())
                    .productName(item.getProductName())
                    .optionName(item.getOptionName())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .lineAmount(item.getLineAmount())
                    .build();
        }
    }
}
