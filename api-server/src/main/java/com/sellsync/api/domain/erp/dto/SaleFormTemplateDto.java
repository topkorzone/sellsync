package com.sellsync.api.domain.erp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 전표입력 템플릿 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleFormTemplateDto {
    
    private UUID id;
    private UUID tenantId;
    private String templateName;
    private Boolean isDefault;
    private Boolean isSystemTemplate;
    private String description;
    
    // 기본값 필드
    private String defaultCustomerCode;
    private String defaultWarehouseCode;
    private String defaultIoType;
    private String defaultEmpCd;
    private String defaultSite;
    private String defaultExchangeType;
    
    private String templateConfig;      // JSON
    private Boolean isActive;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
