package com.sellsync.api.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * VAT 계산 유틸리티
 * 
 * VAT 포함 금액에서 공급가액과 부가세를 계산합니다.
 */
@Component
public class VatCalculator {
    
    private static final BigDecimal VAT_RATE = new BigDecimal("1.1");
    
    /**
     * VAT 포함 금액에서 공급가액 계산
     * SUPPLY_AMT = floor(USER_PRICE_VAT / 1.1)
     * 
     * @param totalWithVat VAT 포함 총액
     * @return 공급가액
     */
    public int calculateSupplyAmount(int totalWithVat) {
        return BigDecimal.valueOf(totalWithVat)
            .divide(VAT_RATE, 0, RoundingMode.FLOOR)
            .intValue();
    }
    
    /**
     * VAT 금액 계산
     * VAT_AMT = USER_PRICE_VAT - SUPPLY_AMT
     * 
     * @param totalWithVat VAT 포함 총액
     * @return VAT 금액
     */
    public int calculateVatAmount(int totalWithVat) {
        int supplyAmt = calculateSupplyAmount(totalWithVat);
        return totalWithVat - supplyAmt;
    }
    
    /**
     * VatBreakdown 반환 (공급가액, 부가세, 총액)
     * 
     * @param totalWithVat VAT 포함 총액
     * @return VAT 분해 정보
     */
    public VatBreakdown breakdown(int totalWithVat) {
        int supplyAmt = calculateSupplyAmount(totalWithVat);
        int vatAmt = totalWithVat - supplyAmt;
        return new VatBreakdown(supplyAmt, vatAmt, totalWithVat);
    }
    
    /**
     * VAT 분해 정보
     */
    @Data
    @AllArgsConstructor
    public static class VatBreakdown {
        /**
         * 공급가액
         */
        private int supplyAmount;
        
        /**
         * 부가세
         */
        private int vatAmount;
        
        /**
         * 총액 (VAT 포함)
         */
        private int totalAmount;
    }
}
