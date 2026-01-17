package com.sellsync.api.domain.erp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErpItemSearchRequest {
    private String keyword;             // 검색어 (품목코드/품목명)
    private String categoryCode;        // 품목분류
    private Integer page;
    private Integer size;
}
