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
    private OrderStatus orderStatus;
    private LocalDateTime paidAt;  // 결재일 (주문일시에서 변경)
    
    // Customer
    private String buyerName;
    private String receiverName;
    private String receiverPhone1;
    
    // Payment
    private Long totalPaidAmount;
    private Long shippingFee;  // 배송비
    private Long commissionAmount;  // 수수료
    private Long expectedSettlementAmount;  // 정산예정금액
    
    // Order Items (간략 정보)
    private List<OrderItemSummary> items;
    
    // 매핑 상태 (MAPPED, UNMAPPED, PARTIAL)
    private String mappingStatus;
    
    // ERP 전표번호
    private String erpDocumentNo;
    
    // 송장번호
    private String trackingNo;
    
    // 택배사명
    private String carrierName;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Order 엔티티로부터 목록용 DTO 생성
     */
    public static OrderListResponse from(Order order) {
        return OrderListResponse.builder()
                .orderId(order.getOrderId())
                .tenantId(order.getTenantId())
                .storeId(order.getStoreId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .orderStatus(order.getOrderStatus())
                .paidAt(order.getPaidAt())  // 결재일로 변경
                .buyerName(order.getBuyerName())
                .receiverName(order.getReceiverName())
                .receiverPhone1(order.getReceiverPhone1())
                .totalPaidAmount(order.getTotalPaidAmount())
                .shippingFee(order.getShippingFee())
                .commissionAmount(order.getCommissionAmount())
                .expectedSettlementAmount(order.getExpectedSettlementAmount())
                .items(order.getItems().stream()
                        .map(OrderItemSummary::from)
                        .collect(Collectors.toList()))
                .mappingStatus(null)  // 서비스에서 별도로 설정
                .erpDocumentNo(null)  // 서비스에서 별도로 설정
                .trackingNo(null)     // 서비스에서 별도로 설정
                .carrierName(null)    // 서비스에서 별도로 설정
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
