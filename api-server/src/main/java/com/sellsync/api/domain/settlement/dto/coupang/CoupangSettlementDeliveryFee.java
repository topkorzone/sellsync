package com.sellsync.api.domain.settlement.dto.coupang;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 쿠팡 배송비 정산 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoupangSettlementDeliveryFee {
    
    /**
     * 배송비 금액
     */
    @JsonProperty("amount")
    private Long amount;
    
    /**
     * 배송비 수수료
     */
    @JsonProperty("fee")
    private Long fee;
    
    /**
     * 배송비 수수료 VAT
     */
    @JsonProperty("feeVat")
    private Long feeVat;
    
    /**
     * 배송비 수수료율 (%)
     */
    @JsonProperty("feeRatio")
    private Double feeRatio;
    
    /**
     * 배송비 정산 금액
     */
    @JsonProperty("settlementAmount")
    private Long settlementAmount;
    
    /**
     * 기본 배송비
     */
    @JsonProperty("baseAmount")
    private Long baseAmount;
    
    /**
     * 기본 배송비 수수료
     */
    @JsonProperty("baseFee")
    private Long baseFee;
    
    /**
     * 기본 배송비 수수료 VAT
     */
    @JsonProperty("baseFeeVat")
    private Long baseFeeVat;
    
    /**
     * 도서산간 배송비
     */
    @JsonProperty("remoteAmount")
    private Long remoteAmount;
    
    /**
     * 도서산간 배송비 수수료
     */
    @JsonProperty("remoteFee")
    private Long remoteFee;
    
    /**
     * 도서산간 배송비 수수료 VAT
     */
    @JsonProperty("remoteFeeVat")
    private Long remoteFeeVat;
}
