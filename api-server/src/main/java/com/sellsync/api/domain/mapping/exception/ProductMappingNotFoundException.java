package com.sellsync.api.domain.mapping.exception;

import com.sellsync.api.domain.order.enums.Marketplace;

import java.util.UUID;

/**
 * 상품 매핑을 찾을 수 없을 때 발생하는 예외
 */
public class ProductMappingNotFoundException extends RuntimeException {

    public ProductMappingNotFoundException(UUID mappingId) {
        super(String.format("ProductMapping not found: %s", mappingId));
    }

    public ProductMappingNotFoundException(Marketplace marketplace, String productId, String sku) {
        super(String.format("ProductMapping not found: marketplace=%s, productId=%s, sku=%s", 
            marketplace, productId, sku));
    }

    public ProductMappingNotFoundException(String message) {
        super(message);
    }
}
