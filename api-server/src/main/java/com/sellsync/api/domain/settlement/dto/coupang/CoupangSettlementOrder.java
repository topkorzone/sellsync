package com.sellsync.api.domain.settlement.dto.coupang;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 쿠팡 정산 주문 데이터
 * 
 * 주문 단위 정산 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoupangSettlementOrder {
    
    /**
     * 주문 번호
     */
    @JsonProperty("orderId")
    private Long orderId;
    
    /**
     * 판매 유형 (SALE, CANCEL, RETURN 등)
     */
    @JsonProperty("saleType")
    private String saleType;
    
    /**
     * 판매일 (yyyy-MM-dd)
     */
    @JsonProperty("saleDate")
    private String saleDate;
    
    /**
     * 매출인식일 (yyyy-MM-dd)
     */
    @JsonProperty("recognitionDate")
    private String recognitionDate;
    
    /**
     * 정산일 (yyyy-MM-dd)
     */
    @JsonProperty("settlementDate")
    private String settlementDate;
    
    /**
     * 최종 정산일 (yyyy-MM-dd)
     */
    @JsonProperty("finalSettlementDate")
    private String finalSettlementDate;
    
    /**
     * 배송비 정산 정보
     */
    @JsonProperty("deliveryFee")
    private CoupangSettlementDeliveryFee deliveryFee;
    
    /**
     * 상품 정산 항목 목록
     */
    @JsonProperty("items")
    private List<CoupangSettlementItem> items;
}
