package com.sellsync.api.domain.erp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder  
public class ErpCustomerDto {
    private String customerCode;
    private String customerName;
    private String bizNo;               // 사업자번호
    private String ceoName;             // 대표자명
    private String customerType;        // 거래처유형
    private boolean isActive;
}
