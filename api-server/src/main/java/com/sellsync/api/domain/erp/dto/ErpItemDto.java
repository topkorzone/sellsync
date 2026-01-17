package com.sellsync.api.domain.erp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErpItemDto {
    private String itemCode;
    private String itemName;
    private String itemSpec;            // 규격
    private String unit;                // 단위
    private Long unitPrice;             // 단가
    private String itemType;            // 품목유형
    private String categoryCode;        // 품목분류 코드
    private String categoryName;        // 품목분류 명칭
    private String warehouseCode;       // 창고코드
    private Integer stockQty;           // 재고수량
    private Integer availableQty;       // 가용수량
    private boolean isActive;
}
