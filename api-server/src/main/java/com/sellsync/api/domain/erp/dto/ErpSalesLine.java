package com.sellsync.api.domain.erp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErpSalesLine {
    private String itemCode;            // 품목코드
    private String itemName;            // 품목명
    private Integer quantity;           // 수량
    private Long unitPrice;             // 단가
    private Long amount;                // 금액
    private Long vatAmount;             // 부가세
    private String warehouseCode;       // 창고코드
    private String remarks;             // 적요
}
