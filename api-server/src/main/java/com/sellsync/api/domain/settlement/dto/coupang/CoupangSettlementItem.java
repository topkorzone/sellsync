package com.sellsync.api.domain.settlement.dto.coupang;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 쿠팡 정산 상품 항목
 * 
 * 개별 상품에 대한 정산 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoupangSettlementItem {
    
    /**
     * 과세 유형 (TAX, TAX_FREE)
     */
    @JsonProperty("taxType")
    private String taxType;
    
    /**
     * 상품 ID
     */
    @JsonProperty("productId")
    private Long productId;
    
    /**
     * 상품명
     */
    @JsonProperty("productName")
    private String productName;
    
    /**
     * 판매자 상품 ID (vendorItemId)
     */
    @JsonProperty("vendorItemId")
    private Long vendorItemId;
    
    /**
     * 판매자 상품명
     */
    @JsonProperty("vendorItemName")
    private String vendorItemName;
    
    /**
     * 판매 가격
     */
    @JsonProperty("salePrice")
    private Long salePrice;
    
    /**
     * 수량
     */
    @JsonProperty("quantity")
    private Integer quantity;
    
    /**
     * 쿠팡 할인 쿠폰
     */
    @JsonProperty("coupangDiscountCoupon")
    private Long coupangDiscountCoupon;
    
    /**
     * 할인 쿠폰 정책 동의 여부
     */
    @JsonProperty("discountCouponPolicyAgreement")
    private Boolean discountCouponPolicyAgreement;
    
    /**
     * 판매 금액
     */
    @JsonProperty("saleAmount")
    private Long saleAmount;
    
    /**
     * 판매자 할인 쿠폰
     */
    @JsonProperty("sellerDiscountCoupon")
    private Long sellerDiscountCoupon;
    
    /**
     * 다운로드 쿠폰
     */
    @JsonProperty("downloadableCoupon")
    private Long downloadableCoupon;
    
    /**
     * 서비스 수수료
     */
    @JsonProperty("serviceFee")
    private Long serviceFee;
    
    /**
     * 서비스 수수료 VAT
     */
    @JsonProperty("serviceFeeVat")
    private Long serviceFeeVat;
    
    /**
     * 서비스 수수료율 (%)
     */
    @JsonProperty("serviceFeeRatio")
    private Double serviceFeeRatio;
    
    /**
     * 정산 금액
     */
    @JsonProperty("settlementAmount")
    private Long settlementAmount;
    
    /**
     * 쿠랑테 수수료율 (%)
     */
    @JsonProperty("couranteeFeeRatio")
    private Double couranteeFeeRatio;
    
    /**
     * 쿠랑테 수수료
     */
    @JsonProperty("couranteeFee")
    private Long couranteeFee;
    
    /**
     * 쿠랑테 수수료 VAT
     */
    @JsonProperty("couranteeFeeVat")
    private Long couranteeFeeVat;
    
    /**
     * 스토어 수수료 할인 VAT
     */
    @JsonProperty("storeFeeDiscountVat")
    private Long storeFeeDiscountVat;
    
    /**
     * 스토어 수수료 할인
     */
    @JsonProperty("storeFeeDiscount")
    private Long storeFeeDiscount;
    
    /**
     * 외부 판매자 SKU 코드
     */
    @JsonProperty("externalSellerSkuCode")
    private String externalSellerSkuCode;
    
    // ===== Helper Methods =====
    
    /**
     * 총 수수료 계산 (서비스 수수료 + 쿠랑테 수수료)
     */
    public Long getTotalCommission() {
        long total = 0L;
        if (serviceFee != null) total += serviceFee;
        if (serviceFeeVat != null) total += serviceFeeVat;
        if (couranteeFee != null) total += couranteeFee;
        if (couranteeFeeVat != null) total += couranteeFeeVat;
        return total;
    }
}
