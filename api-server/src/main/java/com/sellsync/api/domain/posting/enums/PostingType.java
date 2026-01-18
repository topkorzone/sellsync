package com.sellsync.api.domain.posting.enums;

/**
 * 전표 유형 (TRD v1, v3)
 * 
 * [주문 전표 - TRD v1]
 * - PRODUCT_SALES: 상품 매출전표
 * - SHIPPING_FEE: 배송비 매출전표
 * - PRODUCT_CANCEL: 상품 취소전표
 * - SHIPPING_FEE_CANCEL: 배송비 취소전표
 * - DISCOUNT: 할인전표
 * - POINT_USAGE: 포인트 사용전표
 * 
 * [수수료 전표]
 * - PRODUCT_SALES_COMMISSION: 상품판매 수수료
 * - SHIPPING_FEE_COMMISSION: 배송비 수수료
 * 
 * [정산 전표 - TRD v3]
 * - COMMISSION_EXPENSE: 수수료 비용전표 (마켓 + PG 수수료)
 * - SHIPPING_ADJUSTMENT: 배송비 차액전표 (정산 배송비 - 고객 결제 배송비)
 * - RECEIPT: 수금전표 (순 입금액)
 */
public enum PostingType {
    // 주문 전표
    PRODUCT_SALES("상품매출"),
    SHIPPING_FEE("배송비"),
    PRODUCT_CANCEL("상품취소"),
    SHIPPING_FEE_CANCEL("배송비취소"),
    DISCOUNT("할인"),
    POINT_USAGE("포인트사용"),
    
    // 수수료 전표
    PRODUCT_SALES_COMMISSION("상품판매수수료"),
    SHIPPING_FEE_COMMISSION("배송비수수료"),
    
    // 정산 전표 (TRD v3)
    COMMISSION_EXPENSE("수수료비용"),
    SHIPPING_ADJUSTMENT("배송비차액"),
    RECEIPT("수금"),
    
    // Legacy (호환성)
    SETTLEMENT_INCOME("정산수금"),
    SETTLEMENT_EXPENSE("정산비용");

    private final String displayName;

    PostingType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 취소 전표 여부
     */
    public boolean isCancelType() {
        return this == PRODUCT_CANCEL || this == SHIPPING_FEE_CANCEL;
    }

    /**
     * 수수료 전표 여부
     */
    public boolean isCommissionType() {
        return this == PRODUCT_SALES_COMMISSION || this == SHIPPING_FEE_COMMISSION;
    }

    /**
     * 정산 전표 여부 (TRD v3)
     */
    public boolean isSettlementType() {
        return this == COMMISSION_EXPENSE || 
               this == SHIPPING_ADJUSTMENT || 
               this == RECEIPT ||
               this == SETTLEMENT_INCOME ||
               this == SETTLEMENT_EXPENSE;
    }
}
