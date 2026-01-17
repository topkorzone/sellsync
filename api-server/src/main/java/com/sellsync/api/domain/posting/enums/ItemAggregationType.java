package com.sellsync.api.domain.posting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 아이템 집계 방식
 * 
 * ORDER_ITEM 소스 타입에서 여러 개의 아이템을 처리하는 방법
 */
@Getter
@RequiredArgsConstructor
public enum ItemAggregationType {
    
    /**
     * 집계 안 함 (단일 값)
     * ORDER 소스 타입에 사용
     */
    NONE("집계 안 함", "단일 값 (주문 레벨 데이터)"),
    
    /**
     * 합계
     * 예: item1.quantity(2) + item2.quantity(3) = 5
     */
    SUM("합계", "모든 아이템의 값을 합산"),
    
    /**
     * 첫 번째 아이템만
     * 예: item1.productName
     */
    FIRST("첫 번째", "첫 번째 아이템의 값만 사용"),
    
    /**
     * 문자열 연결 (콤마 구분)
     * 예: "상품A, 상품B, 상품C"
     */
    CONCAT("문자열 연결", "모든 아이템의 값을 콤마로 연결"),
    
    /**
     * 별도 라인 생성
     * 각 아이템마다 전표 라인을 생성 (LINE 레벨 필드)
     */
    MULTI_LINE("별도 라인", "각 아이템마다 전표 라인 생성");
    
    private final String displayName;
    private final String description;
}
