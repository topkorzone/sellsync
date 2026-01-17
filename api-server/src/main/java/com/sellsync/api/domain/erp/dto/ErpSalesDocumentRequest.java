package com.sellsync.api.domain.erp.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ErpSalesDocumentRequest {
    private String documentDate;        // 전표일자 (YYYY-MM-DD)
    private String customerCode;        // 거래처코드
    private String warehouseCode;       // 창고코드
    private String remarks;             // 적요
    private List<ErpSalesLine> lines;   // 전표 라인
}
