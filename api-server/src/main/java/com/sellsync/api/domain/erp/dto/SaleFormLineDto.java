package com.sellsync.api.domain.erp.dto;

import com.sellsync.api.domain.erp.entity.SaleFormLine.SaleFormLineStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 전표 라인 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleFormLineDto {
    
    private UUID id;
    private UUID tenantId;
    
    private String uploadSerNo;
    private String ioDate;
    private String cust;
    private String custDes;
    private String empCd;
    private String whCd;
    private String ioType;
    
    private String prodCd;              // 필수
    private String prodDes;
    private String sizeDes;
    private BigDecimal qty;             // 필수
    private BigDecimal price;           // 필수
    private BigDecimal supplyAmt;
    private BigDecimal vatAmt;
    private String remarks;
    
    private String site;
    private String pjtCd;
    private String formData;            // JSON (전체 필드)
    
    private SaleFormLineStatus status;
    private String docNo;
    private String erpResponse;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
