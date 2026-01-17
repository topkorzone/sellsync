package com.sellsync.api.domain.order.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 마켓플레이스 주문 통합 DTO
 */
@Data
@Builder
public class MarketplaceOrderDto {
    
    // 원본
    private String rawPayload;
    
    // 주문 기본
    private String marketplaceOrderId;
    private String bundleOrderId;
    private String orderStatus;
    private LocalDateTime orderedAt;
    private LocalDateTime paidAt;
    
    // 주문자
    private String buyerName;
    private String buyerPhone;
    private String buyerId;
    
    // 수취인
    private String receiverName;
    private String receiverPhone1;
    private String receiverPhone2;
    private String receiverZipCode;
    private String receiverAddress;
    private String safeNumber;
    private String safeNumberType;
    
    // 금액
    private Long totalProductAmount;
    private Long totalDiscountAmount;
    private Long totalShippingAmount;
    private Long totalPaidAmount;
    private Long commissionAmount;  // 마켓 수수료 (OrderItem의 commission 합계)
    private Long expectedSettlementAmount;  // 정산 예정 금액
    
    // 배송비
    private String shippingFeeType;
    private Long shippingFee;
    private Long prepaidShippingFee;
    private Long additionalShippingFee;
    
    // 기타
    private String deliveryRequest;
    private String paymentMethod;
    private String personalCustomsCode;
    private String buyerMemo;
    
    // 상품
    private List<MarketplaceOrderItemDto> items;
}
