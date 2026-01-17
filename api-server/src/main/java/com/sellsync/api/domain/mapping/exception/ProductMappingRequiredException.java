package com.sellsync.api.domain.mapping.exception;

import lombok.Getter;

import java.util.List;

/**
 * 상품 매핑이 필요한 경우 발생하는 예외
 * 
 * ERP 전표 생성 시 모든 상품이 매핑되어 있어야 하며,
 * 매핑되지 않은 상품이 있으면 이 예외를 발생시킨다.
 */
@Getter
public class ProductMappingRequiredException extends RuntimeException {
    
    private final List<String> unmappedItems;
    
    public ProductMappingRequiredException(String message, List<String> unmappedItems) {
        super(message);
        this.unmappedItems = unmappedItems;
    }
    
    public ProductMappingRequiredException(List<String> unmappedItems) {
        super("상품 매핑이 완료되지 않았습니다. 매핑 관리 화면에서 먼저 매핑을 완료해주세요.");
        this.unmappedItems = unmappedItems;
    }
}
