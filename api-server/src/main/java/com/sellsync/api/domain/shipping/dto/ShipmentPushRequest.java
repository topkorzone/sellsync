package com.sellsync.api.domain.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 송장 반영 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentPushRequest {
    
    /**
     * 마켓 주문번호
     */
    private String marketplaceOrderId;
    
    /**
     * 상품 주문번호 (스마트스토어용)
     */
    private String productOrderId;
    
    /**
     * 배송박스 ID (쿠팡용)
     */
    private String shipmentBoxId;
    
    /**
     * 택배사 코드 (내부 표준)
     */
    private String carrierCode;
    
    /**
     * 택배사명
     */
    private String carrierName;
    
    /**
     * 송장번호
     */
    private String trackingNo;
}
