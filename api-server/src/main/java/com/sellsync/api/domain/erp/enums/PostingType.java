package com.sellsync.api.domain.erp.enums;

/**
 * ERP 전표 유형
 */
public enum PostingType {
    PRODUCT_SALES("상품 매출전표"),
    SHIPPING_FEE("배송비 매출전표"),
    PRODUCT_SALES_COMMISSION("상품판매 수수료"),
    SHIPPING_FEE_COMMISSION("배송비 수수료"),
    PRODUCT_CANCEL("상품 취소전표"),
    SHIPPING_CANCEL("배송비 취소전표");
    
    private final String description;
    
    PostingType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
