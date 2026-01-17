package com.sellsync.api.domain.erp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 전표입력 템플릿 생성 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSaleFormTemplateRequest {
    
    private String templateName;        // 필수
    private Boolean isDefault;          // 기본 템플릿 여부
    private String description;
    
    // 기본값 필드
    private String defaultCustomerCode;
    private String defaultWarehouseCode;
    private String defaultIoType;
    private String defaultEmpCd;
    private String defaultSite;
    private String defaultExchangeType;
    
    private String templateConfig;      // JSON
}
