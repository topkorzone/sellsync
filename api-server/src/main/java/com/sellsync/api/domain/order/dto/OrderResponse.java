package com.sellsync.api.domain.order.dto;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private UUID orderId;
    private UUID tenantId;
    private UUID storeId;
    private Marketplace marketplace;
    private String marketplaceOrderId;
    private String bundleOrderId;
    private OrderStatus orderStatus;
    private LocalDateTime orderedAt;
    private LocalDateTime paidAt;
    
    // Customer
    private String buyerName;
    private String buyerPhone;
    private String buyerId;
    private String receiverName;
    private String receiverPhone1;
    private String receiverPhone2;
    private String receiverZipCode;
    private String receiverAddress;
    private String safeNumber;
    private String safeNumberType;
    
    // Payment
    private Long totalProductAmount;
    private Long totalShippingAmount;
    private Long totalDiscountAmount;
    private Long totalPaidAmount;
    private Long commissionAmount;
    private String paymentMethod;
    
    // Shipping
    private String shippingFeeType;
    private Long shippingFee;
    private Long prepaidShippingFee;
    private Long additionalShippingFee;
    private String deliveryRequest;
    private String personalCustomsCode;
    private String buyerMemo;
    
    // Settlement
    private String settlementStatus;
    private LocalDateTime settlementCollectedAt;
    private LocalDate settlementDate;
    private Long productCommissionAmount;  // 상품 수수료 (정산 수집 후)
    private Long shippingCommissionAmount;  // 배송비 수수료 (정산 수집 후)
    
    // Order Items
    private List<OrderItemResponse> items;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .tenantId(order.getTenantId())
                .storeId(order.getStoreId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .bundleOrderId(order.getBundleOrderId())
                .orderStatus(order.getOrderStatus())
                .orderedAt(order.getOrderedAt())
                .paidAt(order.getPaidAt())
                .buyerName(order.getBuyerName())
                .buyerPhone(order.getBuyerPhone())
                .buyerId(order.getBuyerId())
                .receiverName(order.getReceiverName())
                .receiverPhone1(order.getReceiverPhone1())
                .receiverPhone2(order.getReceiverPhone2())
                .receiverZipCode(order.getReceiverZipCode())
                .receiverAddress(order.getReceiverAddress())
                .safeNumber(order.getSafeNumber())
                .safeNumberType(order.getSafeNumberType())
                .totalProductAmount(order.getTotalProductAmount())
                .totalShippingAmount(order.getTotalShippingAmount())
                .totalDiscountAmount(order.getTotalDiscountAmount())
                .totalPaidAmount(order.getTotalPaidAmount())
                .commissionAmount(order.getCommissionAmount())
                .paymentMethod(order.getPaymentMethod())
                .shippingFeeType(order.getShippingFeeType())
                .shippingFee(order.getShippingFee())
                .prepaidShippingFee(order.getPrepaidShippingFee())
                .additionalShippingFee(order.getAdditionalShippingFee())
                .deliveryRequest(order.getDeliveryRequest())
                .personalCustomsCode(order.getPersonalCustomsCode())
                .buyerMemo(order.getBuyerMemo())
                .settlementStatus(order.getSettlementStatus() != null ? order.getSettlementStatus().name() : "NOT_COLLECTED")
                .settlementCollectedAt(order.getSettlementCollectedAt())
                .settlementDate(order.getSettlementDate())
                .productCommissionAmount(order.getCommissionAmount() != null ? order.getCommissionAmount() : 0L)
                .shippingCommissionAmount(order.getShippingCommissionAmount() != null ? order.getShippingCommissionAmount() : 0L)
                .items(order.getItems() != null ? order.getItems().stream()
                        .map(OrderItemResponse::from)
                        .collect(Collectors.toList()) : List.of())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
    
    /**
     * 주문 상품 응답 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemResponse {
        private UUID orderItemId;
        private Integer lineNo;
        private String marketplaceProductId;
        private String marketplaceSku;
        private String productName;
        private String optionName;
        private Integer quantity;
        private Long unitPrice;
        private Long lineAmount;
        
        public static OrderItemResponse from(OrderItem item) {
            return OrderItemResponse.builder()
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
