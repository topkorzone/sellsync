package com.sellsync.api.domain.order.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 마켓플레이스 주문 상품 DTO
 */
@Data
@Builder
public class MarketplaceOrderItemDto {
    private String marketplaceProductId;
    private String marketplaceSku;
    private String productName;
    private String exposedProductName;
    private String optionName;
    private String brandId;
    private Integer quantity;
    private Long unitPrice;
    private Long originalPrice;
    private Long discountAmount;
    private Long lineAmount;
    private Long commissionAmount;  // 상품별 마켓 수수료
    private String itemStatus;
    private String rawPayload;
}
