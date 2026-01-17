package com.sellsync.api.domain.posting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 필드 데이터 소스 타입
 * 
 * 전표 필드의 데이터를 어디서 가져올지 정의
 */
@Getter
@RequiredArgsConstructor
public enum FieldSourceType {
    
    /**
     * 주문(Order) 엔티티에서 가져옴
     * 예: order.marketplaceOrderId, order.buyerName
     */
    ORDER("주문 정보", "Order 엔티티의 필드에서 데이터 추출"),
    
    /**
     * 주문 아이템(OrderItem) 엔티티에서 가져옴
     * 예: item.productName, item.quantity
     * itemAggregation 설정 필요
     */
    ORDER_ITEM("주문 상품", "OrderItem 엔티티의 필드에서 데이터 추출 (여러 아이템 처리 방법 설정 필요)"),
    
    /**
     * 상품 매핑(ProductMapping) 정보에서 가져옴
     * 예: mapping.erpProductCode, mapping.erpProductName
     */
    PRODUCT_MAPPING("상품 매핑", "ProductMapping 정보에서 ERP 상품 코드/이름 추출"),
    
    /**
     * ERP 품목(ErpItem) 정보에서 가져옴
     * 예: erpItem.itemCode, erpItem.itemName, erpItem.unitPrice
     */
    ERP_ITEM("ERP 품목 정보", "ERP 시스템에 등록된 품목 마스터 정보 추출"),
    
    /**
     * 계산식
     * 예: order.totalPaymentAmount / item.quantity
     */
    FORMULA("계산식", "필드 값을 사용한 계산식 (사칙연산, 조건문 등)"),
    
    /**
     * 고정값 사용
     * 예: "MAIN", "판매", "Y"
     */
    FIXED("고정값", "사용자가 지정한 고정값 사용"),
    
    /**
     * 현재 날짜/시간
     * 예: NOW, TODAY
     */
    SYSTEM("시스템 값", "현재 날짜, 시간 등 시스템 값 사용"),
    
    /**
     * 스토어(Store) 정보에서 가져옴
     * 예: store.erpCustomerCode, store.storeName
     */
    STORE("스토어 정보", "Store 엔티티의 필드에서 데이터 추출 (거래처코드 등)"),
    
    /**
     * ERP 설정(ErpConfig) 정보에서 가져옴
     * 예: erpConfig.defaultWarehouseCode, erpConfig.defaultCustomerCode
     */
    ERP_CONFIG("ERP 설정", "ERP 설정에서 기본 창고코드 등 추출");
    
    private final String displayName;
    private final String description;
}
