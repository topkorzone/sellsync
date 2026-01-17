package com.sellsync.api.domain.erp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErpCustomerSearchRequest {
    private String keyword;
    private String customerType;
    private Integer page;
    private Integer size;
}
