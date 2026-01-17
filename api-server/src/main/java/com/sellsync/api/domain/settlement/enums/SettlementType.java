package com.sellsync.api.domain.settlement.enums;

/**
 * 정산 라인 유형
 * 
 * TRD v3 정의:
 * - SALES: 매출 정산
 * - COMMISSION: 수수료
 * - SHIPPING_FEE: 배송비
 * - CLAIM: 클레임(환불/교환)
 * - ADJUSTMENT: 조정
 * - RECEIPT: 수금
 */
public enum SettlementType {
    SALES("매출정산"),
    COMMISSION("수수료"),
    SHIPPING_FEE("배송비"),
    CLAIM("클레임"),
    ADJUSTMENT("조정"),
    RECEIPT("수금");

    private final String displayName;

    SettlementType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
