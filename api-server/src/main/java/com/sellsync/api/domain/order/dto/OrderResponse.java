package com.sellsync.api.domain.order.dto;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

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
    private String paymentMethod;
    
    // Shipping
    private String shippingFeeType;
    private Long shippingFee;
    private Long prepaidShippingFee;
    private Long additionalShippingFee;
    private String deliveryRequest;
    private String personalCustomsCode;
    private String buyerMemo;
    
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
                .paymentMethod(order.getPaymentMethod())
                .shippingFeeType(order.getShippingFeeType())
                .shippingFee(order.getShippingFee())
                .prepaidShippingFee(order.getPrepaidShippingFee())
                .additionalShippingFee(order.getAdditionalShippingFee())
                .deliveryRequest(order.getDeliveryRequest())
                .personalCustomsCode(order.getPersonalCustomsCode())
                .buyerMemo(order.getBuyerMemo())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
